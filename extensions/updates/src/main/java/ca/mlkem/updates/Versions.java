package ca.mlkem.updates;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.*;

/** Conservative Android version-name comparison; never lexicographic numeric sorting. */
public final class Versions {
    private static final Pattern CORE=Pattern.compile("^[vV]?(\\d+(?:[._]\\d+)*)(.*)$");
    private static final Pattern PRE=Pattern.compile("^[._-]?(dev|alpha|beta|preview|rc)[._-]?(\\d*)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern STABLE=Pattern.compile("^[._-]?(?:release|stable|final)(?:[._-][A-Za-z0-9_-]+)?$",Pattern.CASE_INSENSITIVE);
    private Versions() {}
    private static final class Value {List<BigInteger> parts;int stage,number;}
    private static Value parse(String s) {
        if(s==null||s.length()>100)return null;
        Matcher m=CORE.matcher(s);if(!m.matches())return null;
        Value v=new Value();v.parts=new ArrayList<>();
        try {for(String part:m.group(1).split("[._]")){if(part.length()>20)return null;v.parts.add(new BigInteger(part));}}
        catch(NumberFormatException e){return null;}
        String tail=m.group(2);v.stage=5;
        if(!tail.isEmpty()&&!STABLE.matcher(tail).matches()){
            Matcher pre=PRE.matcher(tail);if(!pre.matches())return null;
            v.stage=Arrays.asList("dev","alpha","beta","preview","rc").indexOf(pre.group(1).toLowerCase(Locale.ROOT));
            try {v.number=pre.group(2).isEmpty()?0:Integer.parseInt(pre.group(2));}catch(NumberFormatException e){return null;}
        }
        return v;
    }
    public static Integer compare(String a,String b) {
        Value x=parse(a),y=parse(b);if(x==null||y==null)return null;
        for(int i=0;i<Math.max(x.parts.size(),y.parts.size());i++){
            int order=(i<x.parts.size()?x.parts.get(i):BigInteger.ZERO).compareTo(i<y.parts.size()?y.parts.get(i):BigInteger.ZERO);
            if(order!=0)return order;
        }
        int order=Integer.compare(x.stage,y.stage);return order!=0?order:Integer.compare(x.number,y.number);
    }
    public static boolean newer(String candidate,String installed){Integer order=compare(candidate,installed);return order!=null&&order>0;}
}
