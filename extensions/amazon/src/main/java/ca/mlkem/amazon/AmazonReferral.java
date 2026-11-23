package ca.mlkem.amazon;

import android.net.Uri;
import java.util.List;

/** Classifies the Amazon.ca publisher visible in this link without retaining its value. */
final class AmazonReferral {
    private static final String ESTORE_PUBLISHER = "aeropestor02-20";
    enum Tag {
        ABSENT("No publisher tag visible"), ESTORE("Aeroplan publisher tag observed"),
        OTHER("Different publisher tag observed"), UNCLEAR("Publisher tag unclear");
        final String description;
        Tag(String description) { this.description = description; }
    }
    private AmazonReferral() {}

    static Tag tag(Uri uri) {
        List<String> values = uri.getQueryParameters("tag");
        if (values.isEmpty()) return Tag.ABSENT;
        if (values.size() != 1 || values.get(0).trim().isEmpty()) return Tag.UNCLEAR;
        return ESTORE_PUBLISHER.equals(values.get(0)) ? Tag.ESTORE : Tag.OTHER;
    }
}
