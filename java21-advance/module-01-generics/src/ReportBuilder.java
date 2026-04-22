// ============================================================
//  ReportBuilder.java  —  Scaffolding for Lab 1.2, Activity 3
//  Do NOT add a main() here. Runner lives in Lab_1_2_AdvancedPatterns.java
// ============================================================

// ---------- Data class ----------

class Report {
    private final String title;
    private final String author;
    private final boolean landscape;

    Report(String title, String author, boolean landscape) {
        this.title     = title;
        this.author    = author;
        this.landscape = landscape;
    }

    @Override
    public String toString() {
        return "Report{title='" + title + "', author='" + author +
               "', landscape=" + landscape + "}";
    }
}

// ============================================================
//  THE BROKEN BASE — return type is always BrokenBaseBuilder,
//  so subclass methods vanish after any base-class call.
// ============================================================

class BrokenBaseBuilder {

    protected String title  = "";
    protected String author = "";

    // The crack: returns BrokenBaseBuilder, not the subclass
    public BrokenBaseBuilder title(String title) {
        this.title = title;
        return this;
    }

    public BrokenBaseBuilder author(String author) {
        this.author = author;
        return this;
    }
}

class BrokenPdfReportBuilder extends BrokenBaseBuilder {

    private boolean landscape = false;

    public BrokenPdfReportBuilder landscape() {
        this.landscape = true;
        return this;
    }

    public Report build() {
        return new Report(title, author, landscape);
    }
}

// ============================================================
//  THE FIXED BASE — type parameter T lets each subclass
//  declare "I am the thing that gets returned."
// ============================================================

// TODO 3.1 — add a type parameter <T extends BaseBuilder<T>> to BaseBuilder

    abstract class BaseBuilder<T extends BaseBuilder<T>> {

        protected String title = "";
        protected String author = "";

        // TODO 3.2 — add title and author methods that returns type parameter

        @SuppressWarnings("unchecked")
        public T title(String title) {
            this.title = title;
            return (T) this;
        }

        @SuppressWarnings("unchecked")
        public T author(String author) {
            this.author = author;
            return (T) this;
        }
    }

// TODO 3.3 - Uncomment the class declaration below
  
class PdfReportBuilder extends BaseBuilder<PdfReportBuilder> {

    private boolean landscape = false;

    public PdfReportBuilder landscape() {
        this.landscape = true;
        return this;
    }

    public Report build() {
        return new Report(title, author, landscape);
    }
}
