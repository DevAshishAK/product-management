package com.pcom.invoice.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.pcom.invoice.entity.Invoice;
import com.pcom.invoice.entity.InvoiceLine;
import java.io.ByteArrayOutputStream;
import org.springframework.stereotype.Component;

@Component
public class InvoicePdfGenerator {

    public byte[] render(Invoice invoice) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 50, 50);
            PdfWriter.getInstance(doc, out);
            doc.open();
            doc.add(new Paragraph("INVOICE " + invoice.getInvoiceNumber(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18)));
            doc.add(new Paragraph("Order: " + invoice.getOrderId()));
            doc.add(new Paragraph("Customer: " + invoice.getUserId()));
            doc.add(new Paragraph("Issued: " + invoice.getIssuedAt()));
            doc.add(new Paragraph(" "));
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            for (String h : new String[] {"Item", "Unit price", "Qty", "Total"}) table.addCell(new Phrase(h, bold));
            for (InvoiceLine l : invoice.getLines()) {
                table.addCell(l.getDescription());
                table.addCell(l.getUnitPrice().toPlainString());
                table.addCell(String.valueOf(l.getQuantity()));
                table.addCell(l.getLineTotal().toPlainString());
            }
            doc.add(table);
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("TOTAL: " + invoice.getTotalAmount().toPlainString() + " " + invoice.getCurrency(), bold));
            doc.close();
            return out.toByteArray();
        } catch (DocumentException | java.io.IOException e) {
            throw new IllegalStateException("Cannot render invoice " + invoice.getInvoiceNumber(), e);
        }
    }
}
