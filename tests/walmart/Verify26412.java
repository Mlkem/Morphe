import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.*;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
/** Exact native contracts and hook coverage for Walmart Canada 26.41.2. */
public class Verify26412 {
 static final String TILE="Lcom/walmart/glass/lists/view/lists/ListDetailTileView;";
 static Map<String,ClassDef> classes=new HashMap<>();
 static Set<String> wanted=new HashSet<>(Arrays.asList(TILE,"Lpl0/j;","LwU/d;","Lcom/walmart/glass/integration/root/RootActivity;","Lcom/walmart/glass/lists/domain/ListItem$ListDetailItem;","Lcom/walmart/glass/lists/domain/ListItemProduct;","LV50/q;","LXa1/j;","Landroidx/constraintlayout/widget/ConstraintLayout$LayoutParams;"));
 static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
 static void field(String owner,String name,String type){
   for(Field f:classes.get(owner).getFields()) if(f.getName().equals(name) && f.getType().equals(type))return;
   throw new AssertionError("Missing reflection field "+owner+"->"+name+":"+type);
 }
 static void method(String owner,String name,String ret){
   for(Method m:classes.get(owner).getMethods())if(m.getName().equals(name)&&m.getReturnType().equals(ret))return;
   throw new AssertionError("Missing runtime method "+owner+"->"+name);
 }
 static void getter(String owner,String name,String ret,String target){
   for(Method m:classes.get(owner).getMethods())if(m.getName().equals(name)&&m.getParameterTypes().isEmpty()&&m.getReturnType().equals(ret)&&m.getImplementation()!=null)
     for(Instruction i:m.getImplementation().getInstructions())if(i instanceof ReferenceInstruction){
       Reference r=((ReferenceInstruction)i).getReference();
       if(r instanceof FieldReference){FieldReference f=(FieldReference)r;
         if(f.getDefiningClass().equals(owner)&&f.getName().equals(target)&&f.getType().equals(ret))return;
       }
     }
   throw new AssertionError("Getter does not read expected field: "+owner+"->"+name+" / "+target);
 }
 public static void main(String[]args)throws Exception{
   wanted.add("Lcom/walmart/glass/lists/domain/PriceInfo;");
   wanted.add("Lcom/walmart/glass/lists/view/lists/b;");
   wanted.add("Lcom/walmart/glass/lists/view/lists/d;");
   wanted.add("Landroidx/recyclerview/widget/RecyclerView$A;");
   wanted.addAll(Arrays.asList("Landroidx/recyclerview/widget/z;","Landroidx/recyclerview/widget/h;","LY50/q0;","Lcom/walmart/glass/lists/domain/ListItem$ChecklistItem;","Lcom/walmart/glass/lists/domain/ListItem$AisleHeader;","Lcom/walmart/glass/lists/domain/ProductLocation;","Lcom/walmart/glass/lists/domain/Aisle;","Lcom/walmart/glass/globalscanner/views/GlobalScannerActivity;","Lcom/walmart/glass/lists/view/lists/ListDealsBottomBanner;"));
   wanted.addAll(Arrays.asList("Lcom/walmart/glass/lists/view/lists/i;","Lcom/walmart/glass/ui/product/tile/GridProductTileView;","Lcom/walmart/glass/ui/product/tile/LargeListProductTileView;","Lcom/walmart/glass/ads/api/AdsView;","Lz21/K0;","Lz21/l0;","LY11/L0;","Lcom/walmart/glass/ui/product/tile/section/ProductData;"));
   wanted.addAll(Arrays.asList("LwI/a;","Lcom/walmart/glass/converse/sparkycallout/SparkyTabCallout;","Lcom/walmart/glass/converse/sparkyessentialscallout/SparkyTabEssentialsAeCallOut;","Lcom/walmart/glass/converse/unified/chat/ui/view/ConverseUnifiedFABView;"));
   Map<String,Integer> calls=new TreeMap<>();
   Map<String,Set<String>> sites=new TreeMap<>();
   wanted.add("Lcom/walmart/glass/lists/view/lists/ListsDetailRecyclerView$c;");
   wanted.add("Lcom/walmart/glass/lists/domain/PromotionMessage;");
   try(ZipFile z=new ZipFile(args[0])) {
     for(Enumeration<? extends ZipEntry> es=z.entries();es.hasMoreElements();) {
       ZipEntry e=es.nextElement();if(!e.getName().matches("classes[0-9]*\\.dex"))continue;
       DexBackedDexFile dex=DexBackedDexFile.fromInputStream(Opcodes.forApi(35),new BufferedInputStream(z.getInputStream(e)));
       for(ClassDef c:dex.getClasses()) {
         if(wanted.contains(c.getType())||c.getType().startsWith("Lapp/canada/walmart/")) {
           require(classes.put(c.getType(),c)==null,"Duplicate class: "+c.getType());
           if(wanted.contains(c.getType()))for(Method m:c.getMethods())if(m.getImplementation()!=null)
             for(Instruction ins:m.getImplementation().getInstructions())if(ins instanceof ReferenceInstruction) {
               Reference ref=((ReferenceInstruction)ins).getReference();
               if(ref instanceof MethodReference) {
                 MethodReference mr=(MethodReference)ref;
                 if(mr.getDefiningClass().startsWith("Lapp/canada/walmart/")) {
                   String key=(mr.getDefiningClass().equals("Lapp/canada/walmart/PatchStatus;")?"status_":"")+mr.getName();calls.put(key,calls.getOrDefault(key,0)+1);
                   sites.computeIfAbsent(c.getType()+"->"+m.getName(), k->new HashSet<>()).add(mr.getDefiningClass()+"->"+mr.getName());
                 }
               }
             }
         }
       }
     }
   }
   require(classes.keySet().containsAll(wanted),"Missing expected original classes");
   String p="Lcom/walmart/glass/lists/domain/ListItemProduct;";
   field(p,"e","LM50/e;");field(p,"f","Ljava/lang/String;");
   field(p,"A","Lcom/walmart/glass/lists/domain/PriceInfo;");
   field(p,"i","Ljava/lang/Boolean;");
   for(String n:Arrays.asList("n","o","p","t"))field(p,n,"Ljava/lang/String;");
   field(p,"v","LM50/a0;");
   field("Lcom/walmart/glass/lists/domain/PriceInfo;","f","Ljava/lang/Double;");
   field("Lcom/walmart/glass/lists/domain/PriceInfo;","i","Ljava/lang/Double;");
   field(p,"h","Z");field(p,"z","Lcom/walmart/glass/lists/domain/ProductLocation;");
   field(p,"P","Ljava/util/List;");field(p,"Q","Ljava/util/List;");
   field("Lcom/walmart/glass/lists/domain/PromotionMessage;","h","Ljava/lang/String;");
   field("Lcom/walmart/glass/lists/domain/ProductLocation;","d","Ljava/lang/String;");
   field("Lcom/walmart/glass/lists/domain/ProductLocation;","e","Lcom/walmart/glass/lists/domain/Aisle;");
   field("Lcom/walmart/glass/lists/domain/Aisle;","d","Ljava/lang/String;");
   field("Lcom/walmart/glass/lists/domain/Aisle;","e","Ljava/lang/Integer;");
   method("Lcom/walmart/glass/lists/domain/ListItem$ListDetailItem;","x","Ljava/lang/Integer;");
   method("Lcom/walmart/glass/lists/domain/ListItem$ListDetailItem;","n","Ljava/lang/String;");
   method("Lcom/walmart/glass/lists/domain/ListItem$ChecklistItem;","f","Lcom/walmart/glass/lists/domain/ListItem$ListDetailItem;");
   method("Lcom/walmart/glass/lists/domain/ListItem$ChecklistItem;","g","Z");
   method("Lcom/walmart/glass/lists/domain/ListItem$AisleHeader;","<init>","V");
   method("Landroidx/recyclerview/widget/z;","w","Ljava/lang/Object;");
   method("Landroidx/recyclerview/widget/h;","w","Ljava/util/List;");
   method(TILE,"getListDetailPromoExpiryMessage","Ljava/lang/String;");
   method(TILE,"getListDetailPerEachPrice","Ljava/lang/String;");
   boolean hasItemView=false;
   for(Field f:classes.get("Landroidx/recyclerview/widget/RecyclerView$A;").getFields())if(f.getType().equals("Landroid/view/View;"))hasItemView=true;
   require(hasItemView,"Missing ViewHolder itemView");
   method("Lcom/walmart/glass/lists/domain/ListItem$ListDetailItem;","p",p);
   String detail="Lcom/walmart/glass/lists/domain/ListItem$ListDetailItem;";
   String check="Lcom/walmart/glass/lists/domain/ListItem$ChecklistItem;";
   getter(detail,"p",p,"h");getter(detail,"x","Ljava/lang/Integer;","g");getter(detail,"n","Ljava/lang/String;","d");
   getter(detail,"k","Ljava/lang/String;","e");
   getter(check,"f",detail,"e");getter(check,"g","Z","d");
   for(String adapter:Arrays.asList("Lcom/walmart/glass/lists/view/lists/b;","LY50/q0;"))
     require(classes.get(adapter).getSuperclass().equals("Landroidx/recyclerview/widget/z;"),"Wrong checklist adapter superclass: "+adapter);
   method(TILE,"getActionButton","LV50/q;");
   method(TILE,"getOnActionButtonClicked","Lkotlin/jvm/functions/Function1;");
   for(String n:Arrays.asList("e","f","g","h","i","j","k","l","m","s","t","u","v"))
     field("Landroidx/constraintlayout/widget/ConstraintLayout$LayoutParams;",n,"I");
   field("Landroidx/constraintlayout/widget/ConstraintLayout$LayoutParams;","F","F");
   for(String name:Arrays.asList("selectLists","stock","onSetActionButton","onTileBind","filterNav","defaultTab","history","unitPrice","install","beforeBind","afterBindDeferred","selected","sortAisles","installTotal","installScanner","expiry","hidePromotionBanner","hidePromotionHolder","bindBrowse","dedicated","browse","status_open"))
     require(calls.getOrDefault(name,0)>0,"Missing injected call "+name);
   require(classes.containsKey("Lapp/canada/walmart/CanadaHooks;"),"Missing extension");
   require(classes.containsKey("Lapp/canada/walmart/WalmartFindItemButton;"),"Missing relink extension");
   require(calls.get("beforeBind")==2&&calls.get("afterBindDeferred")==2,"Both checklist adapters must be hooked");
   require(calls.get("sortAisles")==2&&calls.get("onSetActionButton")>0&&calls.get("defaultTab")>0,"Missing return/overload hook");
   field("Lz21/K0;","a","Ljava/lang/String;");field("Lz21/K0;","b","Z");
   field("Lz21/l0;","a","Ljava/lang/String;");field("Lz21/l0;","b","Ljava/lang/Double;");field("Lz21/l0;","o","Z");field("Lz21/l0;","B","Z");
   String data="Lcom/walmart/glass/ui/product/tile/section/ProductData;";
   for(String n:Arrays.asList("g","i","j","k","t"))field(data,n,"Ljava/lang/String;");
   method("LY11/L0;","getData",data);method("LY11/L0;","N","Lz21/l0;");
   Set<String> bind=sites.get("Lcom/walmart/glass/lists/view/lists/i;->U");
   for(String n:Arrays.asList("history","stock","expiry"))require(bind!=null&&bind.contains("Lapp/canada/walmart/TileHooks;->"+n),"Missing common row hook "+n);
   require(!sites.containsKey(TILE+"->setBuyMoreAndSaveView"),"Legacy promotion-only model hook remains");
   require(calls.get("browse")==2,"Both product-card layouts must suppress sponsored cards");
   require(sites.get("Lcom/walmart/glass/ui/product/tile/GridProductTileView;->S1").contains("Lapp/canada/walmart/ListEnhancements;->bindBrowse"),"Missing grid history");
   require(sites.get("Lcom/walmart/glass/ui/product/tile/LargeListProductTileView;->N1").contains("Lapp/canada/walmart/ListEnhancements;->bindBrowse"),"Missing list-card history");
   ClassDef sparky=classes.get("LwI/a;");require(sparky.getInterfaces().contains("LXa1/c;"),"Sparky tab no longer implements the native action-tab contract");
   boolean label=false,icon=false;
   for(Method m:sparky.getMethods())if(m.getImplementation()!=null)for(Instruction i:m.getImplementation().getInstructions()){
     if(m.getName().equals("b")&&i instanceof ReferenceInstruction&&((ReferenceInstruction)i).getReference() instanceof StringReference)label|=((StringReference)((ReferenceInstruction)i).getReference()).getString().equals("Patch tools");
     if(m.getName().equals("h")&&i instanceof NarrowLiteralInstruction)icon|=((NarrowLiteralInstruction)i).getNarrowLiteral()==17301570;
     if(m.getName().equals("c")&&i instanceof ReferenceInstruction){Reference ref=((ReferenceInstruction)i).getReference();require(ref instanceof MethodReference&&((MethodReference)ref).getDefiningClass().equals("Lapp/canada/walmart/PatchStatus;")&&((MethodReference)ref).getName().equals("open"),"Sparky launch/analytics code remains in the tab click action");}
   }
   require(label&&icon,"Missing tools tab label/icon");
   require(!sites.containsKey("Lcom/walmart/glass/integration/root/RootActivity;->onCreateOptionsMenu"),"Obsolete toolbar hook remains");
   require(calls.getOrDefault("suppress",0)==2&&calls.getOrDefault("fab",0)==1,"Missing Sparky nudges/FAB hooks");
   Map<String,String> accessors=new LinkedHashMap<>();
   String[][] expected={{"appVersion","26.41.2"},{"productGetter","p"},{"checklistItemGetter","f"},{"checkedGetter","g"},{"checklistAdapter","com.walmart.glass.lists.view.lists.b"},{"collectedAdapter","Y50.q0"},{"browsePriceGetter","N"},{"actionEnum","V50.q"},{"adapterItemGetter","w"},{"childAdaptersGetter","w"}};
   ClassDef compat=classes.get("Lapp/canada/walmart/RuntimeCompatibility;");require(compat!=null,"Missing compatibility dependency");
   for(Method m:compat.getMethods())if(m.getImplementation()!=null)for(Instruction i:m.getImplementation().getInstructions())if(i instanceof ReferenceInstruction && ((ReferenceInstruction)i).getReference() instanceof StringReference)accessors.put(m.getName(),((StringReference)((ReferenceInstruction)i).getReference()).getString());
   for(String[] pair:expected)require(pair[1].equals(accessors.get(pair[0])),"Wrong runtime accessor "+pair[0]);
   System.out.println("Sparky tab label, tools icon and exclusive tools click verified; old toolbar hook removed.");
   System.out.println("Common list-row binding, browse history, ad containers/cards and patch status hooks verified.");
   System.out.println("Runtime field/method targets present; no duplicate target classes.");
   System.out.println("26.41.2 getter field semantics and both checklist adapter contracts verified.");
   System.out.println("Injected calls: "+calls);
 }
}
