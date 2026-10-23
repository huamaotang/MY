package com.example.ai.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PdfParserService {

    /**
     * Parse a PDF file and extract text content
     *
     * @param pdfFilePath path to the PDF file
     * @return list of text chunks extracted from the PDF
     */
    public List<String> parsePdf(String pdfFilePath) {
        // For now, return mock data
        return java.util.Arrays.asList(
                "这是PDF文档的第一段内容，关于基金投资的基础知识。",
                "这是PDF文档的第二段内容，关于资产配置的策略。",
                "这是PDF文档的第三段内容，关于风险控制的方法。"
        );
    }

    /**
     * Parse multiple PDF files from a directory
     *
     * @parameter directoryPath path to directory containing PDF files
     * @return list of text chunks from all PDFs
     */
    public List<String> parsePdfDirectory(String directoryPath) {
        // For now, return mock data from parsePdf
        return parsePdf("mock.pdf");
    }
}