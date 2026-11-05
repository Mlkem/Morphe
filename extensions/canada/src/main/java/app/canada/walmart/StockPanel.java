package app.canada.walmart;

import android.content.*;
import android.net.Uri;
import android.graphics.Typeface;
import android.text.*;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;

final class StockPanel {
 static void show(Context c){
  LinearLayout box=ToolUi.column(c);
  if(!StockCapture.supported()){ToolUi.text(box,"Stock quantities need the updated Stock Availability patch for Walmart 26.41.2.",17);ToolUi.dialog(c,"Stock checker",box);return;}
  content(c,box);
 }
 static void content(Context c,LinearLayout box){
  ToolUi.note(box,"Search or scan, open the product, then return here for quantities.");
  EditText query=ToolUi.input(box,"Product name or barcode",false);query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
  ToolUi.button(box,"Search Walmart",()->search(c,query));
  query.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH){search(c,query);return true;}return false;});
  ToolUi.button(box,"Scan barcode",()->{ToolsPanel.scan(c);ToolUi.close(c);});
  ToolUi.section(box,"Products opened this session");
  EditText filter=ToolUi.input(box,"Filter loaded products",false);LinearLayout results=new LinearLayout(c);results.setOrientation(1);box.addView(results);
  Runnable render=()->render(results,filter.getText().toString());
  filter.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){render.run();}public void afterTextChanged(Editable e){}});
  render.run();ToolUi.note(box,"Change stores in Walmart and reopen the item to check another location. Pickup quantities are not physical shelf counts. Missing numbers are not supplied by Walmart.");ToolUi.dialog(c,"Stock checker",box);
 }
 static void search(Context c,EditText query){String q=query.getText().toString().trim();if(q.isEmpty()){query.setError("Enter a product name or barcode");return;}open(c,new Uri.Builder().scheme("https").authority("www.walmart.ca").path("/search").appendQueryParameter("q",q).build());}
 static void open(Context c,Uri uri){try{c.startActivity(new Intent(Intent.ACTION_VIEW,uri).setPackage(c.getPackageName()));ToolUi.close(c);}catch(RuntimeException e){ToolUi.message(c,"Open Walmart search","Use Walmart’s search or scanner to open the product, then return to Stock checker.");}}
 static Uri productUri(String value){if(value==null||value.isEmpty())return null;Uri u=Uri.parse(value);if(value.startsWith("/"))u=Uri.parse("https://www.walmart.ca").buildUpon().encodedPath(u.getEncodedPath()).encodedQuery(u.getEncodedQuery()).build();return "https".equals(u.getScheme())&&("www.walmart.ca".equals(u.getHost())||"walmart.ca".equals(u.getHost()))&&u.getUserInfo()==null?u:null;}
 static void render(LinearLayout parent,String query){parent.removeAllViews();Context c=parent.getContext();String q=query.toLowerCase(Locale.ROOT).trim();int count=0;
  for(StockCapture.Product p:StockCapture.snapshot()){
   if(!(p.name+" "+p.id+" "+p.upc).toLowerCase(Locale.ROOT).contains(q))continue;
   count++;LinearLayout card=ToolUi.card(parent);TextView name=ToolUi.text(card,p.name,18);name.setTypeface(null,Typeface.BOLD);
   ToolUi.note(card,"Item "+p.id+(p.seller.isEmpty()?"":" · "+p.seller));
   for(StockCapture.Row r:p.rows){ToolUi.section(card,r.mode+(r.selected?" · selected":""));ToolUi.note(card,r.location);TextView number=ToolUi.text(card,r.quantity,26);number.setTypeface(null,Typeface.BOLD);number.setTextColor(ToolUi.accent(c));ToolUi.note(card,r.status);}
   ToolUi.note(card,"Read at "+DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(p.readAt))+" · reopen the item to refresh");
   Uri uri=productUri(p.url);if(uri!=null)ToolUi.button(card,"Reopen product",()->open(c,uri));
  }
  if(count==0)ToolUi.note(parent,q.isEmpty()?"No product details loaded yet. Search or scan above to get started.":"No loaded products match.");
 }
}
