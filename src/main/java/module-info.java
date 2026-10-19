module com.axora {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires java.sql;
    requires org.xerial.sqlitejdbc;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.datatype.jsr310;

    opens com.axora.app        to javafx.graphics;
    opens com.axora.ui.controller to javafx.fxml;
    opens com.axora.model      to com.fasterxml.jackson.databind;
    
    exports com.axora.app;
    exports com.axora.model;
    exports com.axora.service;
    exports com.axora.dao;
    exports com.axora.dao.sqlite;
    exports com.axora.export;
    exports com.axora.validation;
    exports com.axora.util;
    exports com.axora.config;
}
