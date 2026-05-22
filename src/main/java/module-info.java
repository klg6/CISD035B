module com.example.gridlott {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;


    opens com.example.gridlott to javafx.fxml;
    exports com.example.gridlott;
}