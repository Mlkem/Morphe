package app.canada.walmart;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.*;

/** Reads the reviewed product-detail response. Never probes carts or substitutes order limits. */
public final class StockCapture {
 static final int MAX_PRODUCTS=24;
 static final long MAX_AGE_MS=30*60*1000L;
 private static final LinkedHashMap<String,Product> products=new LinkedHashMap<>();
 public static boolean supported(){return false;} // Set only when the quantity hook is installed.
 static final class Row {
  final String mode,location,status,quantity;final boolean selected;
  Row(String m,String l,String s,String q,boolean chosen){mode=m;location=l;status=s;quantity=q;selected=chosen;}
 }
 static final class Product {
  final String id,name,upc,seller,url;final long readAt;final List<Row> rows;
  Product(String id,String name,String upc,String seller,String url,List<Row> rows){this.id=id;this.name=name;this.upc=upc;this.seller=seller;this.url=url;this.rows=Collections.unmodifiableList(new ArrayList<>(rows));readAt=System.currentTimeMillis();}
 }
 static Object field(Object o,String name){if(o==null)return null;try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(ReflectiveOperationException|SecurityException e){return null;}}
 static String string(Object o){return o instanceof String?((String)o).trim():o instanceof Enum?((Enum<?>)o).name():"";}
 static String first(String... values){for(String s:values)if(s!=null&&!s.isEmpty())return s;return "";}
 static String quantity(Object n,String unit){
  if(!(n instanceof Number))return "Not provided";
  double d=((Number)n).doubleValue();if(Double.isNaN(d)||Double.isInfinite(d)||d<0)return "Not provided";
  BigDecimal value=BigDecimal.valueOf(d).stripTrailingZeros();
  return value.toPlainString()+("EACH".equals(unit)&&value.scale()<=0?(d==1?" unit":" units"):" (Walmart quantity)");
 }
 static String status(Object value){String s=string(value);if(s.equals("IN_STOCK")||s.equals("AVAILABLE"))return "In stock";if(s.equals("OUT_OF_STOCK")||s.equals("UNAVAILABLE"))return "Out of stock";if(s.equals("LIMITED_STOCK"))return "Limited stock";return s.isEmpty()||s.equals("UNKNOWN__")?"Availability unknown":s.replace('_',' ').toLowerCase(Locale.ROOT);}
 static String location(Object option,String mode){
  String name=string(field(option,"storeName")),address=string(field(option,"addressLine"));
  Object wrapper=field(option,"locationAddress"),nativeAddress=field(wrapper,"pickupOptionLocationAddressFragment");
  if(address.isEmpty()){String street=string(field(nativeAddress,"addressLine1")),city=string(field(nativeAddress,"city"));address=street+(street.isEmpty()||city.isEmpty()?"":", ")+city;}
  if(!name.isEmpty()||!address.isEmpty())return name+(name.isEmpty()||address.isEmpty()?"":" · ")+address;
  return first(string(field(option,"locationText")),mode.equals("Shipping")?"Online shipping":"Store not identified");
 }
 static void row(List<Row> rows,Object option,String mode,String unit){if(option==null)return;rows.add(new Row(mode,location(option,mode),status(field(option,"availabilityStatus")),quantity(field(option,"availableQuantity"),unit),Boolean.TRUE.equals(field(option,"selected"))));}
 public static void product(Object nativeProduct){
  try{
   if(nativeProduct==null)return;
   String id=first(string(field(nativeProduct,"usItemId")),string(field(nativeProduct,"id"))),name=string(field(nativeProduct,"name"));if(id.isEmpty()||name.isEmpty())return;
   String unit=string(field(nativeProduct,"salesUnitType"));List<Row> rows=new ArrayList<>();Object options=field(nativeProduct,"fulfillmentOptions");
   if(options instanceof List)for(Object wrapper:(List<?>)options){
    Object option=field(wrapper,"fulfillmentOptionV2Fragment");if(option==null)continue;
    row(rows,field(option,"onPickupOptionV2"),"Pickup",unit);
    row(rows,field(option,"onInStorePurchaseOptionV2"),"In store",unit);
    row(rows,field(option,"onDeliveryOptionV2"),"Delivery",unit);
    row(rows,field(option,"onHOTDeliveryOptionV2"),"Express delivery",unit);
    row(rows,field(option,"onShippingOptionV2"),"Shipping",unit);
   }
   if(rows.isEmpty())rows.add(new Row("Product offer","Store / fulfilment not identified",status(field(nativeProduct,"availabilityStatus")),quantity(field(nativeProduct,"availableQuantity"),unit),false));
   Product p=new Product(id,name,string(field(nativeProduct,"upc")),string(field(nativeProduct,"sellerName")),string(field(nativeProduct,"canonicalUrl")),rows);
   String key=id+"\u001f"+string(field(nativeProduct,"sellerId"))+"\u001f"+string(field(nativeProduct,"offerId"));
   synchronized(products){products.remove(key);products.put(key,p);while(products.size()>MAX_PRODUCTS)products.remove(products.keySet().iterator().next());}
   RuntimeStats.hit("Stock responses read");
  }catch(Exception e){RuntimeStats.error("Stock response",e);}
 }
 static List<Product> snapshot(){synchronized(products){long now=System.currentTimeMillis();products.values().removeIf(p->now-p.readAt>MAX_AGE_MS||now<p.readAt);List<Product> result=new ArrayList<>(products.values());Collections.reverse(result);return result;}}
 static void clear(){synchronized(products){products.clear();}}
}
