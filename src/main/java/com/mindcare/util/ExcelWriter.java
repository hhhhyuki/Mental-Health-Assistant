package com.mindcare.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class ExcelWriter {

    private static final Logger log = LoggerFactory.getLogger(ExcelWriter.class);

    @Value("${mcp.excel.output-dir}")
    private String outputDir;

    private Path filePath;

    @PostConstruct
    public void init() {
        this.filePath = Paths.get(outputDir, "mindcare_records.xlsx");
        File dir = new File(outputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public synchronized void appendRow(String sheetName, String[] headers, String[] rowData) throws IOException {
        File file = filePath.toFile();
        Workbook workbook;
        Sheet sheet;

        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                workbook = new XSSFWorkbook(fis);
            }
            sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                sheet = workbook.createSheet(sheetName);
                writeHeader(sheet, headers);
            }
        } else {
            workbook = new XSSFWorkbook();
            sheet = workbook.createSheet(sheetName);
            writeHeader(sheet, headers);
        }

        Row row = sheet.createRow(sheet.getLastRowNum() + 1);
        for (int i = 0; i < rowData.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(rowData[i] != null ? rowData[i] : "");
        }

        try (FileOutputStream fos = new FileOutputStream(file)) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private void writeHeader(Sheet sheet, String[] headers) {
        Row headerRow = sheet.createRow(0);
        CellStyle style = sheet.getWorkbook().createCellStyle();
        Font font = sheet.getWorkbook().createFont();
        font.setBold(true);
        style.setFont(font);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
            sheet.autoSizeColumn(i);
        }
    }
}