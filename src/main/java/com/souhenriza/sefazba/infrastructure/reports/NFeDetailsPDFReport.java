package com.souhenriza.sefazba.infrastructure.reports;

import com.souhenriza.sefazba.domain.model.sefaz.NFeDetails;
import com.souhenriza.sefazba.domain.model.sefaz.NFeDetailsEvent;
import com.souhenriza.sefazba.domain.model.sefaz.NFeDetailsHeader;
import com.souhenriza.sefazba.domain.model.ReportContext;
import com.souhenriza.sefazba.domain.port.utils.ICMSPartialAnticipation;
import com.souhenriza.sefazba.domain.port.utils.NFeDetailsReport;
import com.souhenriza.sefazba.shared.enums.ReportFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Renders the NF-e competency report as a PDF that mirrors the reference HTML
 * template ("teste-volume-118-notas.html") kept at the project root: same
 * sections, wording and visual language, adapted to PDFBox drawing calls.
 */
@Slf4j
@RequiredArgsConstructor
public class NFeDetailsPDFReport implements NFeDetailsReport {



    private final String accountDeskName;

    private final String accountDeskSlogan;

    private static final float MM = 2.834646f;
    private static final float MARGIN_TOP = 26 * MM;
    private static final float MARGIN_SIDE = 15 * MM;
    private static final float MARGIN_BOTTOM = 18 * MM;

    private static final float ROW_HEIGHT = 15f;
    private static final float HEADER_ROW_HEIGHT = 16f;

    private static final float TITLE_FONT_SIZE = 16f;
    private static final float LEGEND_FONT_SIZE = 9f;
    private static final float SECTION_FONT_SIZE = 12.5f;
    private static final float CAPTION_FONT_SIZE = 8.5f;
    private static final float LABEL_FONT_SIZE = 7.5f;
    private static final float VALUE_FONT_SIZE = 9.5f;
    private static final float PAINEL_LABEL_SIZE = 7.5f;
    private static final float PAINEL_NUMBER_SIZE = 14.5f;
    private static final float PAINEL_APOIO_SIZE = 8f;
    private static final float TABLE_HEADER_SIZE = 7f;
    private static final float TABLE_BODY_SIZE = 8f;
    private static final float FOOTNOTE_SIZE = 7.5f;
    private static final float OBS_TITLE_SIZE = 9.5f;
    private static final float OBS_TEXT_SIZE = 9.5f;
    private static final float OBS_BASE_SIZE = 7.5f;
    private static final float FOOTER_TEXT_SIZE = 7.5f;
    private static final float FIRMA_TITLE_SIZE = 12.5f;
    private static final float FIRMA_SPAN_SIZE = 6.5f;

    private static final PDType1Font FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font FONT_SERIF = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
    private static final PDType1Font FONT_SERIF_BOLD = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);

    private static final Color AZUL = new Color(1, 75, 150);
    private static final Color TINTA = new Color(29, 37, 41);
    private static final Color SUAVE = new Color(109, 117, 125);
    private static final Color LINHA = new Color(224, 228, 232);
    private static final Color LINHA_FORTE = new Color(195, 204, 212);
    private static final Color ALERTA = new Color(157, 34, 34);
    private static final Color ALERTA_FUNDO = new Color(251, 244, 243);
    private static final Color OK = new Color(47, 107, 57);
    private static final Color ZEBRA = new Color(247, 248, 250);


    private static final String LOGO_RESOURCE_PATH = "/imgs/logomarcaescritorio.png";
    private static final float LOGO_HEIGHT = 14 * MM;
    private static final byte[] LOGO_BYTES = loadLogoBytes();

    private static byte[] loadLogoBytes() {
        try (InputStream in = NFeDetailsPDFReport.class.getResourceAsStream(LOGO_RESOURCE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Logo resource not found: " + LOGO_RESOURCE_PATH);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load logo resource: " + LOGO_RESOURCE_PATH, e);
        }
    }

    private static final String TITLE_TEXT = "Relação de notas fiscais de entrada interestadual";
    private static final String LEGEND_TEXT = "Vinculação de MDF-e e vencimento da antecipação parcial do ICMS";
    private static final String SITUACAO_CADASTRAL = "Credenciado";

    private static final String PENDENCIAS_CAPTION = "Fornecedores que entregaram nota sem MDF-e vinculado nesta competência.";
    private static final String DETALHAMENTO_FOOTNOTE = "(1) Nas notas sem MDF-e não há registro da data de entrada da mercadoria na Bahia. "
            + "Foi usada a data de autorização da nota fiscal como referência.";

    private static final String[] PENDENCIAS_HEADERS = {"Fornecedor", "Notas", "Valor", "% da pendência", "Situação mais antiga"};
    private static final float[] PENDENCIAS_RATIOS = {0.40f, 0.10f, 0.18f, 0.2f, 0.17f};
    private static final boolean[] PENDENCIAS_RIGHT_ALIGN = {false, true, true, true, true};

    private static final String[] DETAIL_HEADERS = {"Nº NF", "Data de autorização", "Emissor", "Valor da nota", "Situação MDF-e", "Data do evento", "Vencimento antec. parcial"};
    private static final float[] DETAIL_RATIOS = {0.09f, 0.10f, 0.32f, 0.12f, 0.10f, 0.09f, 0.12f};
    private static final boolean[] DETAIL_RIGHT_ALIGN = {false, false, false, true, false, false, true};
    private static final int DETAIL_EMISSOR_COLUMN = 2;

    private record Observacao(String titulo, String texto, String base) {
    }

    private static final Observacao[] OBSERVACOES = {
            new Observacao(
                    "Sobre o prazo para pagamento do imposto",
                    "O prazo até o dia 25 vale enquanto a empresa estiver credenciada na SEFAZ. Perdido o credenciamento, "
                            + "o imposto passa a ser pago antes de a mercadoria entrar na Bahia, mesmo quando a nota estiver acompanhada do MDF-e.",
                    "RICMS/BA, Decreto nº 13.780/2012, art. 332, III, “b” e § 2º"),
            new Observacao(
                    "Sobre as notas que chegaram sem MDF-e",
                    "Nessas notas o prazo do dia 25 não vale: o imposto deve ser pago antes de a mercadoria entrar na Bahia. "
                            + "Além disso, sem o MDF-e a empresa não pode aproveitar o crédito de ICMS destacado na nota fiscal.",
                    "RICMS/BA, art. 332, § 2º e art. 309, § 9º · Lei nº 7.014/96, art. 12-A"),
            new Observacao(
                    "Sobre o desconto de 20%",
                    "Por ser microempresa ou empresa de pequeno porte, a empresa tem direito a um desconto de 20% no valor do imposto, "
                            + "desde que o pagamento seja feito dentro do prazo. Pago depois do prazo, esse desconto é perdido.",
                    "RICMS/BA, art. 274")
    };

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ssXXX");
    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final NumberFormat CURRENCY_FMT = NumberFormat.getCurrencyInstance(PT_BR);
    private static final NumberFormat NUMBER_FMT = createNumberFormat();

    private static NumberFormat createNumberFormat() {
        NumberFormat fmt = NumberFormat.getNumberInstance(PT_BR);
        fmt.setMinimumFractionDigits(2);
        fmt.setMaximumFractionDigits(2);
        return fmt;
    }

    private final ICMSPartialAnticipation icmsPartialAnticipation;

    @Override
    public ReportFormat supportedFormat() {
        return ReportFormat.PDF;
    }

    private record PendingGroup(String emitter, int count, double total, LocalDate oldestIssueDate, LocalDate dueDate) {
    }

    @Override
    public CompletableFuture<byte[]> generate(List<NFeDetails> nFeDetailsList, ReportContext context) {
        try (PDDocument document = new PDDocument()) {
            nFeDetailsList.sort(Comparator.comparing(r -> parseDate(r.header().issueDate())));

            float pageWidth = PDRectangle.A4.getWidth();
            float pageHeight = PDRectangle.A4.getHeight();
            float contentWidth = pageWidth - 2 * MARGIN_SIDE;

            List<NFeDetails> withMDFe = nFeDetailsList.stream()
                    .filter(d -> findMDFeEvent(d) != null).toList();
            List<NFeDetails> withoutMDFe = nFeDetailsList.stream()
                    .filter(d -> findMDFeEvent(d) == null).toList();

            double totalWithMDFe = sumValues(withMDFe);
            double totalWithoutMDFe = sumValues(withoutMDFe);
            double grandTotal = totalWithMDFe + totalWithoutMDFe;
            long fornecedoresCount = nFeDetailsList.stream().map(d -> d.header().emitter()).distinct().count();

            List<PendingGroup> pendingGroups = withoutMDFe.stream()
                    .collect(Collectors.groupingBy(d -> d.header().emitter()))
                    .entrySet().stream()
                    .map(entry -> {
                        List<NFeDetails> docs = entry.getValue();
                        LocalDate oldest = docs.stream()
                                .map(d -> parseDate(d.header().issueDate()))
                                .min(Comparator.naturalOrder())
                                .orElse(context.reportDate());
                        LocalDate dueDate = icmsPartialAnticipation.getDueDate(oldest, false);
                        return new PendingGroup(entry.getKey(), docs.size(), sumValues(docs), oldest, dueDate);
                    })
                    .sorted(Comparator.comparingDouble(PendingGroup::total).reversed())
                    .toList();

            RenderState state = new RenderState(document, pageWidth, pageHeight, context);
            state.newPage();

            state.y = drawTitleBlock(state.stream, state.y, contentWidth);
            state.y = drawIdentificacao(state.stream, state.y, contentWidth, context);
            state.y = drawPainel(state.stream, state.y, contentWidth, nFeDetailsList.size(), fornecedoresCount,
                    withMDFe.size(), withoutMDFe.size(), totalWithMDFe, totalWithoutMDFe, grandTotal, context);

            state.y = drawSectionTitle(state, "Onde estão as pendências", contentWidth);
            state.y = drawCaption(state.stream, state.y, contentWidth, PENDENCIAS_CAPTION);
            drawPendenciasTable(state, contentWidth, pendingGroups, totalWithoutMDFe, context);
            state.y -= 8 * MM;

            state.y = drawSectionTitle(state, "Detalhamento dos documentos", contentWidth);
            drawDetalhamentoTable(state, contentWidth, nFeDetailsList, grandTotal);
            state.y = drawFootnote(state.stream, state.y, contentWidth);
            state.y -= 8 * MM;

            state.y = ensureSpace(state, 110);
            state.y = drawSectionTitle(state, "Observações importantes", contentWidth);
            drawObservacoes(state, contentWidth);

            state.stream.close();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return CompletableFuture.completedFuture(outputStream.toByteArray());
        } catch (IOException e) {
            log.error("Failed to generate PDF report", e);
            throw new IllegalStateException("Failed to generate PDF report", e);
        }
    }

    private class RenderState {
        PDDocument document;
        PDPageContentStream stream;
        float y;
        float pageWidth;
        float pageHeight;
        ReportContext context;
        PDImageXObject logoImage;

        RenderState(PDDocument document, float pageWidth, float pageHeight, ReportContext context) throws IOException {
            this.document = document;
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
            this.context = context;
            this.logoImage = PDImageXObject.createFromByteArray(document, LOGO_BYTES, "logomarcaescritorio");
        }

        void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            drawHeaderBand(stream, pageWidth, pageHeight, logoImage);
            drawFooterBand(stream, pageWidth, context);
            y = pageHeight - MARGIN_TOP;
        }
    }

    private float ensureSpace(RenderState state, float needed) throws IOException {
        if (state.y - needed < MARGIN_BOTTOM) {
            state.newPage();
        }
        return state.y;
    }

    private boolean isAtPageTop(RenderState state) {
        return state.y >= state.pageHeight - MARGIN_TOP - 0.5f;
    }


    private void drawHeaderBand(PDPageContentStream cs, float pageWidth, float pageHeight, PDImageXObject logoImage) throws IOException {
        float baseline = pageHeight - MARGIN_TOP + 14;

        float logoWidth = LOGO_HEIGHT * logoImage.getWidth() / logoImage.getHeight();
        float logoY = pageHeight - MARGIN_TOP + 6;
        cs.drawImage(logoImage, MARGIN_SIDE, logoY, logoWidth, LOGO_HEIGHT);

        cs.beginText();
        cs.setFont(FONT_SERIF_BOLD, FIRMA_TITLE_SIZE);
        cs.setNonStrokingColor(TINTA);
        float titleWidth = FONT_SERIF_BOLD.getStringWidth(accountDeskName.toUpperCase(Locale.ROOT)) / 1000 * FIRMA_TITLE_SIZE;
        cs.newLineAtOffset(pageWidth - MARGIN_SIDE - titleWidth, baseline);
        cs.showText(accountDeskName.toUpperCase(Locale.ROOT));
        cs.endText();

        cs.beginText();
        cs.setFont(FONT_REGULAR, FIRMA_SPAN_SIZE);
        cs.setNonStrokingColor(AZUL);
        float sloganWidth = FONT_REGULAR.getStringWidth(accountDeskSlogan.toUpperCase(Locale.ROOT)) / 1000 * FIRMA_SPAN_SIZE;
        cs.newLineAtOffset(pageWidth - MARGIN_SIDE - sloganWidth, baseline - 10);
        cs.showText(accountDeskSlogan.toUpperCase(Locale.ROOT));
        cs.endText();

        cs.setStrokingColor(AZUL);
        cs.setLineWidth(1.2f);
        cs.moveTo(MARGIN_SIDE, pageHeight - MARGIN_TOP);
        cs.lineTo(pageWidth - MARGIN_SIDE, pageHeight - MARGIN_TOP);
        cs.stroke();

        cs.setNonStrokingColor(Color.BLACK);
        cs.setStrokingColor(Color.BLACK);
    }

    private void drawFooterBand(PDPageContentStream cs, float pageWidth, ReportContext context) throws IOException {
        float lineY = MARGIN_BOTTOM - 12;
        float textY = lineY - 12;

        cs.setStrokingColor(LINHA);
        cs.setLineWidth(0.8f);
        cs.moveTo(MARGIN_SIDE, lineY);
        cs.lineTo(pageWidth - MARGIN_SIDE, lineY);
        cs.stroke();

        LocalDate competency = context.competencyDate();
        String competencyFmt = String.format("%02d/%d", competency.getMonthValue(), competency.getYear());
        String rightText = safeText(context.companyName()) + " · CNPJ " + formatCnpj(context.cnpj())
                + " · Competência " + competencyFmt;

        cs.beginText();
        cs.setFont(FONT_REGULAR, FOOTER_TEXT_SIZE);
        cs.setNonStrokingColor(SUAVE);
        cs.newLineAtOffset(MARGIN_SIDE, textY);
        cs.endText();

        float rightWidth = FONT_REGULAR.getStringWidth(rightText) / 1000 * FOOTER_TEXT_SIZE;
        cs.beginText();
        cs.setFont(FONT_REGULAR, FOOTER_TEXT_SIZE);
        cs.setNonStrokingColor(SUAVE);
        cs.newLineAtOffset(pageWidth - MARGIN_SIDE - rightWidth, textY);
        cs.showText(rightText);
        cs.endText();

        cs.setNonStrokingColor(Color.BLACK);
        cs.setStrokingColor(Color.BLACK);
    }

    private float drawTitleBlock(PDPageContentStream cs, float y, float contentWidth) throws IOException {
        cs.beginText();
        cs.setFont(FONT_SERIF, TITLE_FONT_SIZE);
        cs.setNonStrokingColor(AZUL);
        cs.newLineAtOffset(MARGIN_SIDE, y - TITLE_FONT_SIZE);
        cs.showText(TITLE_TEXT);
        cs.endText();
        y -= (TITLE_FONT_SIZE + 6);

        cs.beginText();
        cs.setFont(FONT_REGULAR, LEGEND_FONT_SIZE);
        cs.setNonStrokingColor(SUAVE);
        cs.newLineAtOffset(MARGIN_SIDE, y - LEGEND_FONT_SIZE);
        cs.showText(LEGEND_TEXT);
        cs.endText();
        y -= (LEGEND_FONT_SIZE + 14);

        cs.setNonStrokingColor(Color.BLACK);
        return y;
    }

    private static final float[] IDENTIFICACAO_RATIOS = {1.6f, 1.1f, 1.1f, 1f};
    private static final int IDENTIFICACAO_EMPRESA_COLUMN = 0;

    /**
     * The "Empresa" (client) value is rendered as pre-wrapped lines instead of being
     * truncated with an ellipsis, same treatment as the issuer column in the
     * "Detalhamento dos documentos" table. The other single-line values are aligned
     * with the first (top) line of the wrapped company name.
     */
    private float drawIdentificacao(PDPageContentStream cs, float y, float contentWidth, ReportContext ctx) throws IOException {
        LocalDate competency = ctx.competencyDate();
        LocalDate lastDay = competency.withDayOfMonth(competency.lengthOfMonth());
        String periodo = String.format("%02d/%02d a %s", competency.getDayOfMonth(), competency.getMonthValue(), DATE_FMT.format(lastDay));

        String[] labels = {"Empresa", "CNPJ", "Período apurado", "Situação cadastral"};
        String[] values = {safeText(ctx.companyName()), formatCnpj(ctx.cnpj()), periodo, SITUACAO_CADASTRAL};

        float[] colX = ratioPositions(MARGIN_SIDE, contentWidth, IDENTIFICACAO_RATIOS);
        float[] colW = ratioWidths(contentWidth, IDENTIFICACAO_RATIOS);

        float lineHeight = VALUE_FONT_SIZE + 2;
        List<String> empresaLines = wrapText(values[IDENTIFICACAO_EMPRESA_COLUMN], FONT_BOLD, VALUE_FONT_SIZE,
                colW[IDENTIFICACAO_EMPRESA_COLUMN] - 4);
        if (empresaLines.isEmpty()) {
            empresaLines = List.of("-");
        }
        float topBaseline = y - LABEL_FONT_SIZE - VALUE_FONT_SIZE - 3;

        for (int i = 0; i < labels.length; i++) {
            cs.beginText();
            cs.setFont(FONT_REGULAR, LABEL_FONT_SIZE);
            cs.setNonStrokingColor(SUAVE);
            cs.newLineAtOffset(colX[i], y - LABEL_FONT_SIZE);
            cs.showText(labels[i]);
            cs.endText();

            if (i == IDENTIFICACAO_EMPRESA_COLUMN) {
                for (int lineIdx = 0; lineIdx < empresaLines.size(); lineIdx++) {
                    cs.beginText();
                    cs.setFont(FONT_BOLD, VALUE_FONT_SIZE);
                    cs.setNonStrokingColor(TINTA);
                    cs.newLineAtOffset(colX[i], topBaseline - lineIdx * lineHeight);
                    cs.showText(empresaLines.get(lineIdx));
                    cs.endText();
                }
            } else {
                cs.beginText();
                cs.setFont(FONT_BOLD, VALUE_FONT_SIZE);
                cs.setNonStrokingColor(TINTA);
                cs.newLineAtOffset(colX[i], topBaseline);
                float maxWidth = (i + 1 < colX.length ? colX[i + 1] : MARGIN_SIDE + contentWidth) - colX[i] - 4;
                cs.showText(fitText(values[i], maxWidth + 5, FONT_BOLD, VALUE_FONT_SIZE));
                cs.endText();
            }
        }

        float extraLines = Math.max(0, empresaLines.size() - 1);
        y -= (LABEL_FONT_SIZE + VALUE_FONT_SIZE + 14 + extraLines * lineHeight);
        cs.setStrokingColor(LINHA);
        cs.setLineWidth(0.8f);
        cs.moveTo(MARGIN_SIDE, y);
        cs.lineTo(MARGIN_SIDE + contentWidth, y);
        cs.stroke();

        cs.setNonStrokingColor(Color.BLACK);
        return y - 14;
    }

    // ==================== Painel (KPI cards) ====================

    private static final float[] PAINEL_RATIOS = {1f, 1.35f, 1.25f, 1.25f};
    private static final float CARD_HEIGHT = 52f;

    private float drawPainel(PDPageContentStream cs, float y, float contentWidth, int totalDocs, long fornecedoresCount,
                              int countWith, int countWithout, double totalWith, double totalWithout, double grandTotal,
                              ReportContext ctx) throws IOException {
        float[] colX = ratioPositions(MARGIN_SIDE, contentWidth, PAINEL_RATIOS);
        float[] colW = ratioWidths(contentWidth, PAINEL_RATIOS);

        double pctWithout = grandTotal > 0 ? (totalWithout / grandTotal) * 100 : 0;
        String dueDateWith = formatDate(icmsPartialAnticipation.getDueDate(ctx.competencyDate(), true));

        String fornecedoresLabel = fornecedoresCount == 1 ? "fornecedor" : "fornecedores";

        drawCard(cs, colX[0], y, colW[0], "Notas na competência", String.valueOf(totalDocs),
                fornecedoresCount + " " + fornecedoresLabel, false, TINTA);
        drawCard(cs, colX[1], y, colW[1], "Valor total", formatCurrency(grandTotal),
                null , false, TINTA);
        drawCard(cs, colX[2], y, colW[2], "Com MDF-e vinculado", String.valueOf(countWith),
                null, false, TINTA);
        drawCard(cs, colX[3], y, colW[3], "Sem MDF-e vinculado", String.valueOf(countWithout),
                null, true, ALERTA);

        cs.setStrokingColor(LINHA);
        cs.setLineWidth(0.8f);
        cs.addRect(MARGIN_SIDE, y - CARD_HEIGHT, contentWidth, CARD_HEIGHT);
        cs.stroke();
        for (int i = 1; i < colX.length; i++) {
            cs.moveTo(colX[i], y);
            cs.lineTo(colX[i], y - CARD_HEIGHT);
            cs.stroke();
        }

        cs.setNonStrokingColor(Color.BLACK);
        return y - CARD_HEIGHT - 16;
    }

    private void drawCard(PDPageContentStream cs, float x, float y, float width, String label, String number,
                           String apoio, boolean risco, Color numberColor) throws IOException {
        if (risco) {
            cs.setNonStrokingColor(ALERTA_FUNDO);
            cs.addRect(x, y - CARD_HEIGHT, width, CARD_HEIGHT);
            cs.fill();
        }

        float pad = 5 * MM;
        cs.beginText();
        cs.setFont(FONT_REGULAR, PAINEL_LABEL_SIZE);
        cs.setNonStrokingColor(SUAVE);
        cs.newLineAtOffset(x + pad, y - 4 * MM - PAINEL_LABEL_SIZE);
        cs.showText(fitText(label, width - pad, FONT_REGULAR, PAINEL_LABEL_SIZE));
        cs.endText();

        cs.beginText();
        cs.setFont(FONT_BOLD, PAINEL_NUMBER_SIZE);
        cs.setNonStrokingColor(numberColor);
        cs.newLineAtOffset(x + pad, y - 4 * MM - PAINEL_LABEL_SIZE - 4 - PAINEL_NUMBER_SIZE);
        cs.showText(fitText(number, width - pad, FONT_BOLD, PAINEL_NUMBER_SIZE));
        cs.endText();

        cs.beginText();
        cs.setFont(FONT_REGULAR, PAINEL_APOIO_SIZE);
        cs.setNonStrokingColor(risco ? ALERTA : SUAVE);
        cs.newLineAtOffset(x + pad, y - 4 * MM - PAINEL_LABEL_SIZE - 4 - PAINEL_NUMBER_SIZE - 3 - PAINEL_APOIO_SIZE);
        cs.showText(fitText(apoio, width - pad, FONT_REGULAR, PAINEL_APOIO_SIZE));
        cs.endText();

        cs.setNonStrokingColor(Color.BLACK);
    }


    private float drawSectionTitle(RenderState state, String title, float contentWidth) throws IOException {
        state.y = ensureSpace(state, SECTION_FONT_SIZE + 20);
        PDPageContentStream cs = state.stream;

        cs.beginText();
        cs.setFont(FONT_SERIF, SECTION_FONT_SIZE);
        cs.setNonStrokingColor(AZUL);
        cs.newLineAtOffset(MARGIN_SIDE, state.y - SECTION_FONT_SIZE);
        cs.showText(title);
        cs.endText();

        float lineY = state.y - SECTION_FONT_SIZE - 3;
        cs.setStrokingColor(LINHA_FORTE);
        cs.setLineWidth(0.8f);
        cs.moveTo(MARGIN_SIDE, lineY);
        cs.lineTo(MARGIN_SIDE + contentWidth, lineY);
        cs.stroke();

        cs.setNonStrokingColor(Color.BLACK);
        return lineY - 10;
    }

    private float drawCaption(PDPageContentStream cs, float y, float contentWidth, String text) throws IOException {
        cs.beginText();
        cs.setFont(FONT_REGULAR, CAPTION_FONT_SIZE);
        cs.setNonStrokingColor(SUAVE);
        cs.newLineAtOffset(MARGIN_SIDE, y - CAPTION_FONT_SIZE);
        cs.showText(fitText(text, contentWidth, FONT_REGULAR, CAPTION_FONT_SIZE));
        cs.endText();
        cs.setNonStrokingColor(Color.BLACK);
        return y - CAPTION_FONT_SIZE - 8;
    }


    private void drawPendenciasTable(RenderState state, float contentWidth, List<PendingGroup> groups,
                                      double totalPending, ReportContext ctx) throws IOException {
        float[] widths = ratioWidths(contentWidth, PENDENCIAS_RATIOS);
        state.y = drawTableHeaderRow(state.stream, state.y, PENDENCIAS_HEADERS, widths, PENDENCIAS_RIGHT_ALIGN);

        if (groups.isEmpty()) {
            state.stream.beginText();
            state.stream.setFont(FONT_REGULAR, TABLE_BODY_SIZE);
            state.stream.setNonStrokingColor(SUAVE);
            state.stream.newLineAtOffset(MARGIN_SIDE, state.y - ROW_HEIGHT + 5);
            state.stream.showText("Nenhuma pendência identificada nesta competência.");
            state.stream.endText();
            state.stream.setNonStrokingColor(Color.BLACK);
            drawHorizontalLine(state.stream, state.y - ROW_HEIGHT, contentWidth, LINHA);
            state.y -= ROW_HEIGHT;
            return;
        }

        for (int i = 0; i < groups.size(); i++) {
            state.y = ensureSpace(state, ROW_HEIGHT + 5);
            if (isAtPageTop(state)) {
                state.y = drawTableHeaderRow(state.stream, state.y, PENDENCIAS_HEADERS, widths, PENDENCIAS_RIGHT_ALIGN);
            }

            PendingGroup group = groups.get(i);
            double pct = totalPending > 0 ? (group.total() / totalPending) * 100 : 0;
            long overdueDays = ChronoUnit.DAYS.between(group.dueDate(), ctx.reportDate());
            String tag = overdueDays >= 0 ? ("vencido há " + overdueDays + " d") : ("vence em " + (-overdueDays) + " d");

            if (i % 2 == 1) {
                drawZebra(state.stream, state.y, contentWidth);
            }

            String[] values = {formatStringCapitalize(group.emitter()), String.valueOf(group.count()), formatNumber(group.total()),
                    formatPercent(pct), null};

            drawRowCells(state.stream, state.y, values, widths, PENDENCIAS_RIGHT_ALIGN, ALERTA);
            drawTag(state.stream, state.y, widths, tag);

            drawHorizontalLine(state.stream, state.y - ROW_HEIGHT, contentWidth, LINHA);
            state.y -= ROW_HEIGHT;
        }

        int totalCount = groups.stream().mapToInt(PendingGroup::count).sum();
        String[] totalRow = {"Total", String.valueOf(totalCount), formatNumber(totalPending), "100,0%", ""};
        drawTableFooterRow(state.stream, state.y, totalRow, widths, PENDENCIAS_RIGHT_ALIGN);
        state.y -= ROW_HEIGHT;
    }

    private void drawTag(PDPageContentStream cs, float y, float[] widths, String text) throws IOException {
        float colX = MARGIN_SIDE;
        for (float w : widths) colX += w;

        float fontSize = 7.5f;
        float textWidth = FONT_BOLD.getStringWidth(text) / 1000 * fontSize;
        float padX = 4f;
        float boxWidth = textWidth + padX * 2;
        float boxHeight = 11f;
        float boxX = colX - boxWidth - 2;
        float boxY = y - ROW_HEIGHT + 3;

        cs.setStrokingColor(ALERTA);
        cs.setLineWidth(0.6f);
        cs.addRect(boxX, boxY, boxWidth, boxHeight);
        cs.stroke();

        cs.beginText();
        cs.setFont(FONT_BOLD, fontSize);
        cs.setNonStrokingColor(ALERTA);
        cs.newLineAtOffset(boxX + padX, boxY + 2.5f);
        cs.showText(text);
        cs.endText();

        cs.setNonStrokingColor(Color.BLACK);
        cs.setStrokingColor(Color.BLACK);
    }

    private void drawDetalhamentoTable(RenderState state, float contentWidth, List<NFeDetails> allDetails, double grandTotal) throws IOException {
        float[] widths = ratioWidths(contentWidth, DETAIL_RATIOS);
        state.y = drawTableHeaderRow(state.stream, state.y, DETAIL_HEADERS, widths, DETAIL_RIGHT_ALIGN);

        for (int i = 0; i < allDetails.size(); i++) {
            NFeDetails details = allDetails.get(i);
            NFeDetailsHeader header = details.header();
            NFeDetailsEvent mdfeEvent = findMDFeEvent(details);
            boolean hasMDFe = mdfeEvent != null;

            LocalDate issueDate = parseDate(header.issueDate());
            LocalDate eventDate = hasMDFe ? parseDate(mdfeEvent.authorizationDate()) : issueDate;
            String dueDate;

            if (!hasMDFe) {
                dueDate = formatDate(icmsPartialAnticipation.getDueDate(eventDate, false)).concat("¹");
            } else  {
                dueDate = formatDate(icmsPartialAnticipation.getDueDate(eventDate, true));
            }

            String situacao = hasMDFe ? "Com MDF-e" : "Sem MDF-e";
            String eventDateStr = hasMDFe ? formatDateString(mdfeEvent.authorizationDate()) : "-";
            String emissor = formatStringCapitalize(header.emitter().toUpperCase());

            List<String> emissorLines = wrapText(emissor, FONT_REGULAR, TABLE_BODY_SIZE, widths[DETAIL_EMISSOR_COLUMN] - 4);
            if (emissorLines.isEmpty()) {
                emissorLines = List.of("-");
            }
            float lineHeight = TABLE_BODY_SIZE + 2;
            float rowHeight = Math.max(ROW_HEIGHT, emissorLines.size() * lineHeight + 6);

            state.y = ensureSpace(state, rowHeight + 5);
            if (isAtPageTop(state)) {
                state.y = drawTableHeaderRow(state.stream, state.y, DETAIL_HEADERS, widths, DETAIL_RIGHT_ALIGN);
            }

            if (i % 2 == 1) {
                drawZebra(state.stream, state.y, contentWidth, rowHeight);
            }

            String[] values = {header.number(), formatDateString(header.issueDate()), null,
                    formatNumber(parseMoneyValue(header.totalValue())), situacao, eventDateStr, dueDate};

            drawDetalhamentoRow(state.stream, state.y, rowHeight, values, emissorLines, widths, DETAIL_RIGHT_ALIGN,
                    hasMDFe ? OK : ALERTA);

            drawHorizontalLine(state.stream, state.y - rowHeight, contentWidth, LINHA);
            state.y -= rowHeight;
        }

        state.y = ensureSpace(state, ROW_HEIGHT + 5);
        if (isAtPageTop(state)) {
            state.y = drawTableHeaderRow(state.stream, state.y, DETAIL_HEADERS, widths, DETAIL_RIGHT_ALIGN);
        }
        float labelWidth = widths[0] + widths[1] + widths[2];
        float[] mergedWidths = {labelWidth, widths[3], widths[4], widths[5], widths[6]};
        boolean[] mergedRightAlign = {false, true, false, false, false};
        String[] totalRow = {"Total da competência", formatNumber(grandTotal), "", "", ""};
        drawTableFooterRow(state.stream, state.y, totalRow, mergedWidths, mergedRightAlign);
        state.y -= ROW_HEIGHT;
    }

    private float drawFootnote(PDPageContentStream cs, float y, float contentWidth) throws IOException {
        y -= 6;
        List<String> lines = wrapText(DETALHAMENTO_FOOTNOTE, FONT_REGULAR, FOOTNOTE_SIZE, contentWidth);
        cs.setNonStrokingColor(SUAVE);
        for (String line : lines) {
            cs.beginText();
            cs.setFont(FONT_REGULAR, FOOTNOTE_SIZE);
            cs.newLineAtOffset(MARGIN_SIDE, y - FOOTNOTE_SIZE);
            cs.showText(line);
            cs.endText();
            y -= (FOOTNOTE_SIZE + 3);
        }
        cs.setNonStrokingColor(Color.BLACK);
        return y;
    }

    // ==================== Observações ====================

    private void drawObservacoes(RenderState state, float contentWidth) throws IOException {
        for (Observacao obs : OBSERVACOES) {
            List<String> textLines = wrapText(obs.texto(), FONT_REGULAR, OBS_TEXT_SIZE, contentWidth);
            float blockHeight = OBS_TITLE_SIZE + 4 + textLines.size() * (OBS_TEXT_SIZE + 3) + 4 + OBS_BASE_SIZE + 10;
            state.y = ensureSpace(state, blockHeight);

            PDPageContentStream cs = state.stream;
            cs.beginText();
            cs.setFont(FONT_BOLD, OBS_TITLE_SIZE);
            cs.setNonStrokingColor(TINTA);
            cs.newLineAtOffset(MARGIN_SIDE, state.y - OBS_TITLE_SIZE);
            cs.showText(obs.titulo());
            cs.endText();
            state.y -= (OBS_TITLE_SIZE + 4);

            cs.setNonStrokingColor(TINTA);
            for (String line : textLines) {
                cs.beginText();
                cs.setFont(FONT_REGULAR, OBS_TEXT_SIZE);
                cs.newLineAtOffset(MARGIN_SIDE, state.y - OBS_TEXT_SIZE);
                cs.showText(line);
                cs.endText();
                state.y -= (OBS_TEXT_SIZE + 3);
            }
            state.y -= 1;

            cs.beginText();
            cs.setFont(FONT_REGULAR, OBS_BASE_SIZE);
            cs.setNonStrokingColor(SUAVE);
            cs.newLineAtOffset(MARGIN_SIDE, state.y - OBS_BASE_SIZE);
            cs.showText(obs.base());
            cs.endText();
            state.y -= (OBS_BASE_SIZE + 10);

            cs.setNonStrokingColor(Color.BLACK);
        }
    }


    private float drawTableHeaderRow(PDPageContentStream cs, float y, String[] headers, float[] widths, boolean[] rightAlign) throws IOException {
        float lineHeight = TABLE_HEADER_SIZE + 2;
        List<List<String>> wrappedHeaders = new ArrayList<>();
        int maxLines = 1;
        for (int i = 0; i < headers.length; i++) {
            List<String> lines = wrapText(headers[i], FONT_BOLD, TABLE_HEADER_SIZE, widths[i] - 4);
            wrappedHeaders.add(lines);
            maxLines = Math.max(maxLines, lines.size());
        }
        float rowHeight = Math.max(HEADER_ROW_HEIGHT, maxLines * lineHeight + 6);
        float lastLineBaseline = y - rowHeight + 5;

        cs.setNonStrokingColor(SUAVE);
        float x = MARGIN_SIDE;
        for (int i = 0; i < headers.length; i++) {
            List<String> lines = wrappedHeaders.get(i);
            for (int lineIdx = 0; lineIdx < lines.size(); lineIdx++) {
                float baseline = lastLineBaseline + (lines.size() - 1 - lineIdx) * lineHeight;
                cs.beginText();
                drawCellText(cs, lines.get(lineIdx), x, baseline, widths[i], rightAlign[i], FONT_BOLD, TABLE_HEADER_SIZE);
                cs.endText();
            }
            x += widths[i];
        }

        cs.setStrokingColor(LINHA_FORTE);
        cs.setLineWidth(0.8f);
        cs.moveTo(MARGIN_SIDE, y - rowHeight);
        cs.lineTo(MARGIN_SIDE + totalWidth(widths), y - rowHeight);
        cs.stroke();

        cs.setNonStrokingColor(Color.BLACK);
        cs.setStrokingColor(Color.BLACK);
        return y - rowHeight;
    }

    private void drawTableFooterRow(PDPageContentStream cs, float y, String[] values, float[] widths, boolean[] rightAlign) throws IOException {
        cs.setStrokingColor(AZUL);
        cs.setLineWidth(1.2f);
        cs.moveTo(MARGIN_SIDE, y);
        cs.lineTo(MARGIN_SIDE + totalWidth(widths), y);
        cs.stroke();

        cs.setNonStrokingColor(TINTA);
        float x = MARGIN_SIDE;
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null && !values[i].isEmpty()) {
                cs.beginText();
                drawCellText(cs, values[i], x, y - ROW_HEIGHT + 5, widths[i], rightAlign[i], FONT_BOLD, TABLE_BODY_SIZE);
                cs.endText();
            }
            x += widths[i];
        }
        cs.setNonStrokingColor(Color.BLACK);
    }

    private void drawRowCells(PDPageContentStream cs, float y, String[] values, float[] widths, boolean[] rightAlign, Color textColor) throws IOException {
        cs.setNonStrokingColor(textColor);
        float x = MARGIN_SIDE;
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                cs.beginText();
                drawCellText(cs, values[i], x, y - ROW_HEIGHT + 5, widths[i], rightAlign[i], FONT_REGULAR, TABLE_BODY_SIZE);
                cs.endText();
            }
            x += widths[i];
        }
        cs.setNonStrokingColor(Color.BLACK);
    }

    private void drawRowCellsWithColumnColor(PDPageContentStream cs, float y, String[] values, float[] widths, boolean[] rightAlign,
                                              int highlightedColumn, Color highlightColor) throws IOException {
        float x = MARGIN_SIDE;
        for (int i = 0; i < values.length; i++) {
            cs.setNonStrokingColor(i == highlightedColumn ? highlightColor : TINTA);
            cs.beginText();
            drawCellText(cs, values[i], x, y - ROW_HEIGHT + 5, widths[i], rightAlign[i], FONT_REGULAR, TABLE_BODY_SIZE);
            cs.endText();
            x += widths[i];
        }
        cs.setNonStrokingColor(Color.BLACK);
    }

    /**
     * Draws one "Detalhamento dos documentos" row. The document number (index 0) is
     * always rendered in full, never truncated. The issuer (DETAIL_EMISSOR_COLUMN) is
     * rendered as pre-wrapped lines instead of being truncated with an ellipsis. All
     * other single-line cells are aligned with the first (top) issuer line.
     */
    private void drawDetalhamentoRow(PDPageContentStream cs, float y, float rowHeight, String[] values,
                                      List<String> emissorLines, float[] widths, boolean[] rightAlign,
                                      Color highlightColor) throws IOException {
        float lineHeight = TABLE_BODY_SIZE + 2;
        float lastLineBaseline = y - rowHeight + 5;
        float topBaseline = lastLineBaseline + (Math.max(1, emissorLines.size()) - 1) * lineHeight;

        float x = MARGIN_SIDE;
        for (int i = 0; i < values.length; i++) {
            float colWidth = widths[i];
            Color color = i == 4 ? highlightColor : TINTA;
            cs.setNonStrokingColor(color);

            if (i == DETAIL_EMISSOR_COLUMN) {
                for (int lineIdx = 0; lineIdx < emissorLines.size(); lineIdx++) {
                    float baseline = topBaseline - lineIdx * lineHeight;
                    cs.beginText();
                    cs.setFont(FONT_REGULAR, TABLE_BODY_SIZE);
                    cs.newLineAtOffset(x + 1, baseline);
                    cs.showText(emissorLines.get(lineIdx));
                    cs.endText();
                }
            } else if (i == 0) {
                cs.beginText();
                drawCellTextNoTruncate(cs, values[i], x, topBaseline, colWidth, rightAlign[i], FONT_REGULAR, TABLE_BODY_SIZE);
                cs.endText();
            } else {
                cs.beginText();
                drawCellText(cs, values[i], x, topBaseline, colWidth, rightAlign[i], FONT_REGULAR, TABLE_BODY_SIZE);
                cs.endText();
            }
            x += colWidth;
        }
        cs.setNonStrokingColor(Color.BLACK);
    }

    private void drawCellText(PDPageContentStream cs, String text, float colX, float baselineY, float colWidth,
                               boolean rightAlign, PDType1Font font, float fontSize) throws IOException {
        String fitted = fitText(text, colWidth - 4, font, fontSize);
        cs.setFont(font, fontSize);
        float startX;
        if (rightAlign) {
            float textWidth = font.getStringWidth(fitted) / 1000 * fontSize;
            startX = colX + colWidth - 2 - textWidth;
        } else {
            startX = colX + 1;
        }
        cs.newLineAtOffset(startX, baselineY);
        cs.showText(fitted);
    }

    private void drawCellTextNoTruncate(PDPageContentStream cs, String text, float colX, float baselineY, float colWidth,
                                         boolean rightAlign, PDType1Font font, float fontSize) throws IOException {
        String full = safeText(text);
        cs.setFont(font, fontSize);
        float startX;
        if (rightAlign) {
            float textWidth = font.getStringWidth(full) / 1000 * fontSize;
            startX = colX + colWidth - 2 - textWidth;
        } else {
            startX = colX + 1;
        }
        cs.newLineAtOffset(startX, baselineY);
        cs.showText(full);
    }

    private void drawZebra(PDPageContentStream cs, float y, float contentWidth) throws IOException {
        drawZebra(cs, y, contentWidth, ROW_HEIGHT);
    }

    private void drawZebra(PDPageContentStream cs, float y, float contentWidth, float rowHeight) throws IOException {
        cs.setNonStrokingColor(ZEBRA);
        cs.addRect(MARGIN_SIDE, y - rowHeight, contentWidth, rowHeight);
        cs.fill();
        cs.setNonStrokingColor(Color.BLACK);
    }

    private void drawHorizontalLine(PDPageContentStream cs, float y, float tableWidth, Color color) throws IOException {
        cs.setStrokingColor(color);
        cs.setLineWidth(0.5f);
        cs.moveTo(MARGIN_SIDE, y);
        cs.lineTo(MARGIN_SIDE + tableWidth, y);
        cs.stroke();
        cs.setStrokingColor(Color.BLACK);
    }



    private float[] ratioWidths(float totalWidth, float[] ratios) {
        float sum = 0;
        for (float r : ratios) sum += r;
        float[] widths = new float[ratios.length];
        for (int i = 0; i < ratios.length; i++) {
            widths[i] = totalWidth * (ratios[i] / sum);
        }
        return widths;
    }

    private float[] ratioPositions(float start, float totalWidth, float[] ratios) {
        float[] widths = ratioWidths(totalWidth, ratios);
        float[] positions = new float[ratios.length];
        float x = start;
        for (int i = 0; i < ratios.length; i++) {
            positions[i] = x;
            x += widths[i];
        }
        return positions;
    }

    private float totalWidth(float[] widths) {
        float total = 0;
        for (float w : widths) total += w;
        return total;
    }

    private List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        String[] words = safeText(text).split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            float width = font.getStringWidth(candidate) / 1000 * fontSize;
            if (width > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }


    private NFeDetailsEvent findMDFeEvent(NFeDetails details) {
        return details.events().stream()
                .filter(e -> e.event() != null && (e.event().toUpperCase().contains("MDFE")
                        || e.event().toUpperCase().contains("MDF-E")))
                .min(Comparator.comparing(NFeDetailsEvent::authorizationDate))
                .orElse(null);
    }

    private double sumValues(List<NFeDetails> list) {
        return list.stream()
                .mapToDouble(d -> parseMoneyValue(d.header().totalValue()))
                .sum();
    }


    private String safeText(String text) {
        return text != null ? text : "-";
    }

    private String fitText(String text, float columnWidth, PDType1Font font, float fontSize) {
        String safe = safeText(text);
        try {
            float fullWidth = font.getStringWidth(safe) / 1000 * fontSize;
            if (fullWidth <= columnWidth) return safe;

            String ellipsis = "...";
            float ellipsisWidth = font.getStringWidth(ellipsis) / 1000 * fontSize;
            for (int i = safe.length() - 1; i > 0; i--) {
                float truncatedWidth = font.getStringWidth(safe.substring(0, i)) / 1000 * fontSize;
                if (truncatedWidth + ellipsisWidth <= columnWidth) {
                    return safe.substring(0, i) + ellipsis;
                }
            }
            return ellipsis;
        } catch (IOException e) {
            return safe;
        }
    }

    private LocalDate parseDate(String text) {
        if (text == null) return LocalDate.now();
        try {
            return OffsetDateTime.parse(text, DATE_TIME_FMT).toLocalDate();
        } catch (Exception e) {
            try {
                return LocalDate.parse(text, DATE_FMT);
            } catch (Exception ex) {
                return LocalDate.now();
            }
        }
    }

    private String formatDate(LocalDate date) {
        return date != null ? DATE_FMT.format(date) : "-";
    }

    private String formatDateString(String dateStr) {
        if (dateStr == null) return "-";
        try {
            return DATE_FMT.format(parseDate(dateStr));
        } catch (Exception e) {
            return dateStr;
        }
    }

    private String formatCnpj(String cnpj) {
        if (cnpj == null || cnpj.length() != 14) return safeText(cnpj);
        return cnpj.substring(0, 2) + "." + cnpj.substring(2, 5) + "." +
                cnpj.substring(5, 8) + "/" + cnpj.substring(8, 12) + "-" + cnpj.substring(12);
    }

    private double parseMoneyValue(String text) {
        if (text == null || text.isBlank()) return 0;
        String cleaned = text.replaceAll("[R$\\s]", "");
        if (cleaned.contains(",") && cleaned.contains(".")) {
            cleaned = cleaned.replace(".", "").replace(",", ".");
        } else if (cleaned.contains(",")) {
            cleaned = cleaned.replace(",", ".");
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String formatCurrency(double value) {
        return CURRENCY_FMT.format(value);
    }

    private String formatNumber(double value) {
        return NUMBER_FMT.format(value);
    }

    private String formatPercent(double pct) {
        return String.format(PT_BR, "%.1f%%", pct);
    }

    private String formatStringCapitalize(String issuerName) {
        String[] strings = issuerName.split(" ");

        StringBuilder builder = new StringBuilder();

        for (String s : strings) {
            String nameCapitalized = s.substring(0,1).toUpperCase() + s.substring(1).toLowerCase(Locale.ROOT);

            builder.append(nameCapitalized).append(" ");
        }

        return builder.toString();
    }
}
