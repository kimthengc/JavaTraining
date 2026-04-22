import java.util.List;

/**
 * Domain record used across Lab 2.2.
 *
 * Kept deliberately small — the interesting behaviour is in the stream
 * pipelines, not in the model.
 *
 * The seed list in SAMPLE is chosen to make grouping, partitioning, and
 * downstream-collector examples produce visibly different shapes:
 *   - 3 regions (APAC, EMEA, AMER)
 *   - 4 categories (BOOKS, ELECTRONICS, CLOTHING, HOME)
 *   - amounts spanning 40..2400 so a 1000-threshold partition is non-trivial
 *   - several customers repeat across regions to make groupingBy + mapping
 *     produce interesting (non-singleton) downstream lists
 */
public record Order(String customer, String region, String category, double amount) {

    public static final List<Order> SAMPLE = List.of(
            new Order("Acme",       "APAC", "BOOKS",       120.00),
            new Order("Acme",       "APAC", "ELECTRONICS", 1850.00),
            new Order("Beacon",     "APAC", "BOOKS",        45.00),
            new Order("Beacon",     "APAC", "CLOTHING",    240.00),
            new Order("Cyrus",      "APAC", "HOME",        980.00),

            new Order("Delta",      "EMEA", "ELECTRONICS", 2400.00),
            new Order("Delta",      "EMEA", "BOOKS",        75.00),
            new Order("Echo",       "EMEA", "CLOTHING",    180.00),
            new Order("Echo",       "EMEA", "CLOTHING",    220.00),
            new Order("Foxtrot",    "EMEA", "HOME",        610.00),
            new Order("Foxtrot",    "EMEA", "ELECTRONICS", 1300.00),

            new Order("Golf",       "AMER", "BOOKS",        40.00),
            new Order("Golf",       "AMER", "BOOKS",        95.00),
            new Order("Hotel",      "AMER", "ELECTRONICS", 1580.00),
            new Order("Hotel",      "AMER", "HOME",        430.00),
            new Order("India",      "AMER", "CLOTHING",    360.00),
            new Order("India",      "AMER", "CLOTHING",    410.00),
            new Order("Juliet",     "AMER", "HOME",        820.00)
    );
}
