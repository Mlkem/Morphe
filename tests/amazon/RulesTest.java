package ca.mlkem.amazon;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class RulesTest {
    private static int checks;
    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }
    public static void main(String[] args) throws Exception {
        check(SessionRules.validNumber("000 000 000"), "Portal accepts spaced 9-digit identifiers");
        check(SessionRules.validNumber("000\u00a0000\u00a0000"), "Normalize nonbreaking spaces");
        check(!SessionRules.validNumber("00000000"), "Reject eight digits");
        check(!SessionRules.validNumber("0000000000"), "Reject ten digits");
        check(!SessionRules.validNumber("00000000x"), "Reject nonnumeric input");
        check(SessionRules.validLastName("O’Example-Smith"), "Accept names with punctuation");
        check(SessionRules.validLastName("Échantillon"), "Accept Unicode names");
        check(!SessionRules.validLastName(" "), "Reject empty name");
        check(!SessionRules.validLastName("Example\nSmith"), "Reject control characters");
        check(SessionRules.canAutofill(SessionRules.ESTORE_URL), "Allow only the official merchant page");
        check(SessionRules.canAutofill(SessionRules.ESTORE_URL + "?x=1#content-section"), "Allow page query and fragment");
        for (String url : new String[] {
                "http://aeroplan.rewardops.com/en-CA/affiliate-details/amazon",
                "https://aeroplan.rewardops.com.evil.test/en-CA/affiliate-details/amazon",
                "https://aeroplan.rewardops.com@evil.test/en-CA/affiliate-details/amazon",
                "https://evil.test@aeroplan.rewardops.com/en-CA/affiliate-details/amazon",
                "https://aeroplan.rewardops.com:444/en-CA/affiliate-details/amazon",
                "https://aeroplan.rewardops.com/en-CA/affiliate-details/not-amazon",
                "https://www.amazon.ca/", "javascript:alert(1)", "file:///tmp/page.html", "not a URL"
        }) check(!SessionRules.canAutofill(url), "Reject unsafe autofill target: " + url);
        check(SessionRules.isAmazon("https://www.amazon.ca/dp/EXAMPLE123?tag=test"), "Recognize Amazon Canada");
        check(!SessionRules.isAmazon("https://www.amazon.ca.evil.test/"), "Reject spoofed Amazon host");
        check(!SessionRules.isAmazon("https://www.amazon.com/"), "Only Amazon Canada is supported");
        String hostileName = "Example\";globalThis.pwned=true;//\\\u2028";
        Files.createDirectories(Paths.get(args[0]));
        Files.write(Paths.get(args[0], "autofill-fixture.js"),
                AutofillScript.create("000000000", hostileName).getBytes(StandardCharsets.UTF_8));
        Files.write(Paths.get(args[0], "autofill-manual-fixture.js"),
                AutofillScript.create("000000000", hostileName, true).getBytes(StandardCharsets.UTF_8));
        Files.write(Paths.get(args[0], "expected-name.txt"), hostileName.getBytes(StandardCharsets.UTF_8));
        System.out.println("PASS: " + checks + " account-validation and origin checks");
    }
}
