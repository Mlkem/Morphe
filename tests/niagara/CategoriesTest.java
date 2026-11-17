import ca.mlkem.niagara.Categories;
public final class CategoriesTest {
    private static void check(boolean value, String why) { if (!value) throw new AssertionError(why); }
    public static void main(String[] args) {
        check(Categories.classify(0,0,"com.test.racing","Racing")==0,"Android game metadata");
        check(Categories.classify(-1,0x02000000,"com.test.older","Older")==0,"Legacy game flag");
        check(Categories.classify(-1,0,"com.test.port","Truckful")==0,"Truckful without manifest category");
        check(Categories.classify(-1,0,"com.test.truckful","Renamed")==0,"Truckful package");
        check(Categories.classify(-1,0,"com.trucking.dispatch","Truck dispatch")==9,"Do not mistake real trucking tools for games");
        check(Categories.classify(-1,0,"com.unknown.bank","Unknown bank")==9,"No guesses from private app names");
        for(int i=0;i<=8;i++) check(Categories.classify(i,0,"any","any")==i,"Android category "+i);
        check(Categories.classify(99,0,"future","future")==9,"Unknown future category");
        check(Categories.eligible(110,100,true,true,false,false,false,true),"new install");
        check(!Categories.eligible(90,100,true,true,false,false,false,true),"ordinary update of pre-existing app");
        check(!Categories.eligible(110,100,false,true,false,false,false,true),"master off");
        check(!Categories.eligible(110,100,true,false,false,false,false,true),"category off");
        check(!Categories.eligible(110,100,true,true,true,false,false,true),"repeat event");
        check(!Categories.eligible(110,100,true,true,false,true,false,true),"manual hidden app");
        check(!Categories.eligible(110,100,true,true,false,false,true,true),"favorite preservation");
        check(!Categories.eligible(110,100,true,true,false,false,false,false),"private or work profile");
        check(Categories.eligible(90,0,true,true,false,false,false,true),"explicit existing-app sort");
        System.out.println("PASS: categories, Truckful, install/update, toggles, repeat events and preservation");
    }
}
