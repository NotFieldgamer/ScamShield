package com.verity.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The first-column CSV reader: quoting, header/blank handling, and the row cap. */
class CsvTextExtractorTest {

    @Test
    void takesFirstColumnOfEachRow() {
        List<String> texts = CsvTextExtractor.firstColumn("Earn daily from home,Acme\nData entry role,Globex\n", 100);
        assertThat(texts).containsExactly("Earn daily from home", "Data entry role");
    }

    @Test
    void skipsAKnownHeaderRow() {
        List<String> texts = CsvTextExtractor.firstColumn("text,company\nWork from home,Acme\n", 100);
        assertThat(texts).containsExactly("Work from home");
    }

    @Test
    void keepsAFirstRowThatIsNotAKnownHeader() {
        List<String> texts = CsvTextExtractor.firstColumn("Urgent hiring,Acme\nData entry,Globex\n", 100);
        assertThat(texts).containsExactly("Urgent hiring", "Data entry");
    }

    @Test
    void honoursQuotedCommasAndNewlines() {
        String csv = "text\n\"Pay a fee, then start\",Acme\n\"Line one\nLine two\",Globex\n";
        List<String> texts = CsvTextExtractor.firstColumn(csv, 100);
        assertThat(texts).containsExactly("Pay a fee, then start", "Line one\nLine two");
    }

    @Test
    void unescapesDoubledQuotes() {
        List<String> texts = CsvTextExtractor.firstColumn("\"He said \"\"urgent\"\" hiring\"\n", 100);
        assertThat(texts).containsExactly("He said \"urgent\" hiring");
    }

    @Test
    void dropsBlankRowsAndHandlesNoTrailingNewline() {
        List<String> texts = CsvTextExtractor.firstColumn("First posting\n\n\nSecond posting", 100);
        assertThat(texts).containsExactly("First posting", "Second posting");
    }

    @Test
    void capsTheNumberOfRows() {
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            csv.append("posting ").append(i).append('\n');
        }
        List<String> texts = CsvTextExtractor.firstColumn(csv.toString(), 100);
        assertThat(texts).hasSize(100);
        assertThat(texts.get(0)).isEqualTo("posting 0");
    }

    @Test
    void emptyInputYieldsNothing() {
        assertThat(CsvTextExtractor.firstColumn("", 100)).isEmpty();
        assertThat(CsvTextExtractor.firstColumn("\n\n", 100)).isEmpty();
    }

    @Test
    void handlesCrlfLineEndings() {
        List<String> texts = CsvTextExtractor.firstColumn("Alpha,x\r\nBeta,y\r\n", 100);
        assertThat(texts).containsExactly("Alpha", "Beta");
    }
}
