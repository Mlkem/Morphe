package app.canada.walmart;

import android.app.AlertDialog;
import android.content.*;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Shared, theme-aware tools UI. Pages share one dialog and a real Back stack. */
final class ToolUi {
 private static final Map<Context,WeakReference<Navigator>> screens=new WeakHashMap<>();
 static int dp(Context c,int n){return Math.round(c.getResources().getDisplayMetrics().density*n);}
 static boolean dark(Context c){return (c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
 static int theme(Context c){return dark(c)?android.R.style.Theme_Material_Dialog_Alert:android.R.style.Theme_Material_Light_Dialog_Alert;}
 private static final class ThemedContext extends ContextThemeWrapper {ThemedContext(Context c){super(c,theme(c));}}
 static int ink(Context c){return dark(c)?0xfff1f5f9:0xff172b42;}
 static int muted(Context c){return dark(c)?0xffbac9d8:0xff526577;}
 static int accent(Context c){return dark(c)?0xff8cc9ff:0xff005da8;}
 static LinearLayout column(Context c){if(!(c instanceof ThemedContext))c=new ThemedContext(c);LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);int p=dp(c,16);v.setPadding(p,p,p,p);return v;}
 static TextView text(LinearLayout parent,String value,int sp){TextView t=new TextView(parent.getContext());t.setText(value);t.setTextColor(ink(t.getContext()));t.setTextSize(sp);t.setPadding(0,dp(t.getContext(),6),0,dp(t.getContext(),6));parent.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;}
 static TextView note(LinearLayout parent,String value){TextView t=text(parent,value,14);t.setTextColor(muted(t.getContext()));return t;}
 static void section(LinearLayout parent,String value){TextView t=text(parent,value,14);t.setTypeface(null,Typeface.BOLD);t.setTextColor(muted(t.getContext()));t.setPadding(0,dp(t.getContext(),18),0,dp(t.getContext(),6));t.setAccessibilityHeading(true);}
 static LinearLayout card(LinearLayout parent){Context c=parent.getContext();LinearLayout box=column(c);GradientDrawable shape=new GradientDrawable();shape.setColor(dark(c)?0xff233448:0xfff0f6fc);shape.setCornerRadius(dp(c,16));shape.setStroke(dp(c,1),dark(c)?0xff3e5268:0xffdbe7f3);box.setBackground(new RippleDrawable(ColorStateList.valueOf(dark(c)?0x338cc9ff:0x220071ce),shape,null));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(c,5),0,dp(c,5));parent.addView(box,lp);return box;}
 static View action(LinearLayout parent,String title,String subtitle,int icon,Runnable action){Context c=parent.getContext();LinearLayout row=card(parent);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(c,76));ImageView image=new ImageView(c);image.setImageResource(icon);image.setColorFilter(accent(c));row.addView(image,new LinearLayout.LayoutParams(dp(c,26),dp(c,26)));LinearLayout labels=new LinearLayout(c);labels.setOrientation(1);labels.setPadding(dp(c,14),0,dp(c,10),0);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));TextView name=text(labels,title,18);name.setTypeface(null,Typeface.BOLD);note(labels,subtitle);TextView chevron=new TextView(c);chevron.setText("›");chevron.setTextColor(accent(c));chevron.setTextSize(28);row.addView(chevron);row.setFocusable(true);row.setContentDescription(title+". "+subtitle);for(int i=0;i<row.getChildCount();i++)row.getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);row.setAccessibilityDelegate(new View.AccessibilityDelegate(){@Override public void onInitializeAccessibilityNodeInfo(View host,android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(host,info);info.setClassName(Button.class.getName());}});row.setOnClickListener(v->run(c,title,action));return row;}
 static void run(Context c,String label,Runnable action){try{action.run();}catch(Exception e){RuntimeStats.error(label,e);message(c,"Unavailable","This tool could not complete. See Help & diagnostics for details.");}}
 static Button button(LinearLayout parent,String label,Runnable action){Button b=new Button(parent.getContext());b.setText(label);b.setAllCaps(false);b.setMinHeight(dp(b.getContext(),48));parent.addView(b,new LinearLayout.LayoutParams(-1,-2));b.setOnClickListener(v->run(parent.getContext(),label,action));return b;}
 static EditText input(LinearLayout parent,String hint,boolean number){EditText e=new EditText(parent.getContext());e.setHint(hint);e.setTextColor(ink(e.getContext()));e.setHintTextColor(muted(e.getContext()));e.setContentDescription(hint);e.setMinHeight(dp(e.getContext(),48));e.setSingleLine(true);if(number)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);parent.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
 private static Navigator current(Context c){WeakReference<Navigator> ref=screens.get(c);Navigator n=ref==null?null:ref.get();return n!=null&&n.dialog.isShowing()?n:null;}
 static AlertDialog home(Context c,String title,LinearLayout body){close(c);return dialog(c,title,body);}
 static void close(Context c){Navigator n=current(c);if(n!=null)n.dialog.dismiss();}
 static AlertDialog dialog(Context c,String title,LinearLayout body){Navigator n=current(c);if(n==null){n=new Navigator(c);screens.put(c,new WeakReference<>(n));}n.push(title,body);return n.dialog;}
 private static final class Page {final String title;final LinearLayout body;int scroll;Page(String t,LinearLayout b){title=t;body=b;}}
 private static final class Navigator {
  final Context context;final AlertDialog dialog;final ScrollView scroll;final ArrayList<Page> pages=new ArrayList<>();
  Navigator(Context c){context=c;scroll=new ScrollView(c){@Override protected void onMeasure(int w,int h){int max=(int)(getResources().getDisplayMetrics().heightPixels*.72f);int available=MeasureSpec.getMode(h)==MeasureSpec.UNSPECIFIED?max:Math.min(max,MeasureSpec.getSize(h));super.onMeasure(w,MeasureSpec.makeMeasureSpec(available,MeasureSpec.AT_MOST));}};scroll.setFillViewport(false);dialog=new AlertDialog.Builder(c,theme(c)).setTitle("Patch tools").setView(scroll).setNegativeButton("Close",(d,w)->{}).setNeutralButton("Back",null).create();dialog.setOnDismissListener(d->{pages.clear();screens.remove(c);});dialog.setOnKeyListener((d,key,event)->{if(key==KeyEvent.KEYCODE_BACK&&pages.size()>1){if(event.getAction()==KeyEvent.ACTION_UP)back();return true;}return false;});}
  void push(String title,LinearLayout body){if(!pages.isEmpty())pages.get(pages.size()-1).scroll=scroll.getScrollY();pages.add(new Page(title,body));if(!dialog.isShowing()){dialog.show();Window w=dialog.getWindow();if(w!=null){w.setLayout(Math.min(context.getResources().getDisplayMetrics().widthPixels-dp(context,24),dp(context,560)),-2);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->back());}render();}
  void back(){if(pages.size()>1){pages.remove(pages.size()-1);render();}}
  void render(){Page p=pages.get(pages.size()-1);scroll.removeAllViews();p.body.setBackgroundColor(dark(context)?0xff172333:0xffffffff);scroll.addView(p.body);dialog.setTitle(p.title);dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setVisibility(pages.size()>1?View.VISIBLE:View.GONE);scroll.post(()->scroll.scrollTo(0,p.scroll));}
 }
 static void message(Context c,String title,String text){new AlertDialog.Builder(c).setTitle(title).setMessage(text).setPositiveButton("OK",null).show();}
 static void copy(Context c,String label,String text){((ClipboardManager)c.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(label,text));Toast.makeText(c,"Copied "+label,Toast.LENGTH_SHORT).show();}
 static void share(Context c,String title,String text){Intent i=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,title).putExtra(Intent.EXTRA_TEXT,text);c.startActivity(Intent.createChooser(i,title));}
 static void export(Context c,String title,String text){new AlertDialog.Builder(c).setTitle(title).setItems(new String[]{"Copy to clipboard","Share…"},(d,w)->{if(w==0)copy(c,title,text);else share(c,title,text);}).setNegativeButton("Cancel",null).show();}
 static void confirm(Context c,String title,String message,Runnable action){new AlertDialog.Builder(c).setTitle(title).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Confirm",(d,w)->action.run()).show();}
}
