package app.canada.walmart;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowAlertDialog;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(manifest=Config.NONE,sdk=35)
public class StockToolsTest {
 Activity a;
 @Before public void setup(){a=Robolectric.buildActivity(Activity.class).setup().get();StockCapture.clear();}
 @After public void cleanup(){ToolUi.close(a);a.finish();}
 enum Unit {EACH,WEIGHT}
 enum Status {IN_STOCK,OUT_OF_STOCK}
 static class Product {String usItemId="10001",name="Example coffee",upc="123456789012",sellerName="Walmart",sellerId="W",offerId="A",canonicalUrl="/en/ip/example/10001";Unit salesUnitType=Unit.EACH;Double availableQuantity=99.0;Status availabilityStatus=Status.IN_STOCK;List<Object> fulfillmentOptions=new ArrayList<>();}
 static class Wrapper {Object fulfillmentOptionV2Fragment;Wrapper(Object o){fulfillmentOptionV2Fragment=o;}}
 static class Options {Object onPickupOptionV2,onDeliveryOptionV2,onShippingOptionV2,onInStorePurchaseOptionV2,onHOTDeliveryOptionV2;}
 static class Option {Double availableQuantity;Integer maxOrderQuantity=99;Double orderLimit=99.0;String storeName="Store A",addressLine="1 Main Street",locationText="";Status availabilityStatus=Status.IN_STOCK;Boolean selected=true;Option(Double n){availableQuantity=n;}}
 Product product(Double quantity){Product p=new Product();Options o=new Options();o.onPickupOptionV2=new Option(quantity);p.fulfillmentOptions.add(new Wrapper(o));return p;}
 View find(View v,String label){if(v instanceof TextView&&label.equals(((TextView)v).getText().toString()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),label);if(found!=null)return found;}return null;}
 View find(String label){return find(ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView(),label);}
 void clickCard(String label){View v=find(label);while(v!=null&&!v.isClickable())v=(View)v.getParent();assertNotNull(v);v.performClick();}
 @Test public void actualQuantityWinsOverPurchaseLimit(){StockCapture.product(product(7.0));StockCapture.Row r=StockCapture.snapshot().get(0).rows.get(0);assertEquals("7 units",r.quantity);assertEquals("Pickup",r.mode);assertEquals("Store A · 1 Main Street",r.location);}
 @Test public void missingQuantityNeverFallsBackToOrderLimitOrProductWideCount(){StockCapture.product(product(null));assertEquals("Not provided",StockCapture.snapshot().get(0).rows.get(0).quantity);}
 @Test public void zeroAndUnknownStayDistinctAndWeightIsNotCalledUnits(){assertEquals("0 units",StockCapture.quantity(0.0,"EACH"));assertEquals("Not provided",StockCapture.quantity(-1,"EACH"));assertEquals("Not provided",StockCapture.quantity(Double.NaN,"EACH"));assertEquals("Not provided",StockCapture.quantity(Double.POSITIVE_INFINITY,"EACH"));assertEquals("2.75 (Walmart quantity)",StockCapture.quantity(2.75,"WEIGHT"));}
 @Test public void fulfilmentOptionsStaySeparateIncludingUnknownShelfCount(){Product p=product(4.0);Options o=(Options)((Wrapper)p.fulfillmentOptions.get(0)).fulfillmentOptionV2Fragment;o.onShippingOptionV2=new Option(20.0);o.onInStorePurchaseOptionV2=new Option(null);StockCapture.product(p);List<StockCapture.Row> rows=StockCapture.snapshot().get(0).rows;assertEquals(3,rows.size());assertEquals("4 units",rows.get(0).quantity);assertEquals("In store",rows.get(1).mode);assertEquals("Not provided",rows.get(1).quantity);assertEquals("Shipping",rows.get(2).mode);assertEquals("20 units",rows.get(2).quantity);}
 @Test public void reopeningItemReplacesOldStoreAndMissingCounts(){StockCapture.product(product(9.0));Product next=product(null);Option o=(Option)((Options)((Wrapper)next.fulfillmentOptions.get(0)).fulfillmentOptionV2Fragment).onPickupOptionV2;o.storeName="Store B";StockCapture.product(next);assertEquals(1,StockCapture.snapshot().size());StockCapture.Row row=StockCapture.snapshot().get(0).rows.get(0);assertTrue(row.location.startsWith("Store B"));assertEquals("Not provided",row.quantity);}
 @Test public void sellerOffersAreKeptSeparateAndSessionCacheIsBounded(){Product p=product(2.0);StockCapture.product(p);p.offerId="B";StockCapture.product(p);assertEquals(2,StockCapture.snapshot().size());for(int i=0;i<40;i++){p.usItemId="item"+i;StockCapture.product(p);}assertEquals(24,StockCapture.snapshot().size());StockCapture.clear();assertTrue(StockCapture.snapshot().isEmpty());}
 @Test public void inStoreAbsenceIsNotTurnedIntoZero(){StockCapture.product(product(null));LinearLayout rows=ToolUi.column(a);StockPanel.render(rows,"");assertNotNull(find(rows,"Not provided"));assertNull(find(rows,"0 units"));assertNotNull(find(rows,"Pickup · selected"));StockPanel.render(rows,"does not exist");assertNotNull(find(rows,"No loaded products match."));StockPanel.render(rows,"123456789012");assertNotNull(find(rows,"Example coffee"));}
 @Test public void menuBackReusesDialogAndNoWatchlistEntry(){ToolsPanel.show(a);AlertDialog first=ShadowAlertDialog.getLatestAlertDialog();assertNull(find("Watchlist"));clickCard("Shopping tools");assertSame(first,ShadowAlertDialog.getLatestAlertDialog());assertNotNull(find("Compare unit prices"));first.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();assertNotNull(find("Stock checker"));assertEquals(View.GONE,first.getButton(AlertDialog.BUTTON_NEUTRAL).getVisibility());clickCard("Help & diagnostics");assertNotNull(find("Feature guide"));}
 @Test public void searchEscapesQueryAndStaysInWalmart(){EditText q=new EditText(a);q.setText("coffee & tea #1");StockPanel.search(a,q);Intent i=Shadows.shadowOf(a).getNextStartedActivity();assertEquals(a.getPackageName(),i.getPackage());assertEquals("www.walmart.ca",i.getData().getHost());assertEquals("coffee & tea #1",i.getData().getQueryParameter("q"));assertNull(i.getData().getFragment());}
 @Test public void productLinksRejectExternalAndMalformedDestinations(){assertNull(StockPanel.productUri("https://evil.test/x"));assertNull(StockPanel.productUri("javascript:alert(1)"));assertNull(StockPanel.productUri("https://user@walmart.ca/x"));assertEquals("www.walmart.ca",StockPanel.productUri("/en/ip/coffee/10001").getHost());}
 @Test public void emptySearchShowsInputErrorAndDoesNotNavigate(){EditText q=new EditText(a);q.setText(" ");StockPanel.search(a,q);assertNotNull(q.getError());assertNull(Shadows.shadowOf(a).getNextStartedActivity());}
 @Test public void stockPageProvidesSearchAndEmptyState(){StockPanel.content(a,ToolUi.column(a));assertNotNull(find("Search Walmart"));assertNotNull(find("Scan barcode"));assertNotNull(find("No product details loaded yet. Search or scan above to get started."));}
 @Test @Config(qualifiers="w400dp-h880dp-night") @GraphicsMode(GraphicsMode.Mode.NATIVE) public void renderDarkMenu()throws Exception {ToolsPanel.show(a);render("tools-menu-dark.png");assertTrue(ToolUi.dark(a));}
 @Test @Config(qualifiers="w400dp-h880dp") @GraphicsMode(GraphicsMode.Mode.NATIVE) public void renderMenuAndStockForVisualReview()throws Exception {ToolsPanel.show(a);render("tools-menu.png");StockCapture.product(product(7.0));StockPanel.content(a,ToolUi.column(a));render("stock-checker.png");}
 void render(String filename)throws Exception {View view=ShadowAlertDialog.getLatestAlertDialog().getWindow().getDecorView();int width=ToolUi.dp(a,400),height=ToolUi.dp(a,880);view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.AT_MOST));view.layout(0,0,width,view.getMeasuredHeight());Bitmap b=Bitmap.createBitmap(width,view.getMeasuredHeight(),Bitmap.Config.ARGB_8888);view.draw(new Canvas(b));File f=new File("build/ui-review",filename);f.getParentFile().mkdirs();try(OutputStream out=new FileOutputStream(f)){b.compress(Bitmap.CompressFormat.PNG,100,out);}assertTrue(f.length()>1000);}
}
