package com.resort.platform.exports;

/** Os quatro arquivos da §18, com o nome do arquivo em português. */
public enum ExportType {
    LEADS("leads"),
    VISITS("visitas"),
    COMPANIONS("acompanhantes"),
    ACCESS("acessos");

    private final String fileName;

    ExportType(String fileName) {
        this.fileName = fileName;
    }

    public String fileName() {
        return fileName;
    }
}
