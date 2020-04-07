package views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

public class HomePanel extends BorderPane {
    private Label title;

    private Button connect;

    public HomePanel() {
        setMinSize(500, 500);
        title = new Label("ChatamuCentral");
        connect = new Button("Connexion");
        connect.setMinWidth(100);
        VBox gamesButton = new VBox(title, connect);
        VBox.setMargin(title, new Insets(10));
        BorderPane.setAlignment(gamesButton, Pos.CENTER);
        setCenter(gamesButton);
        gamesButton.setAlignment(Pos.CENTER);
        gamesButton.setSpacing(10);
    }

    public Button getConnect() {
        return connect;
    }
}
