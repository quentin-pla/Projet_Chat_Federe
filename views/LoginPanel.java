package views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

/**
 * Page d'accueil
 */
public class LoginPanel extends BorderPane {
    private Label title;
    private TextField username;
    private Button connect;

    public LoginPanel() {
        setMinSize(500, 300);
        title = new Label("Authentification");
        connect = new Button("Valider");
        connect.setMinWidth(100);
        username = new TextField();
        username.setMaxWidth(100);
        username.setPromptText("Identifiant");
        VBox gamesButton = new VBox(title, username, connect);
        VBox.setMargin(title, new Insets(10));
        BorderPane.setAlignment(gamesButton, Pos.CENTER);
        setCenter(gamesButton);
        gamesButton.setAlignment(Pos.CENTER);
        gamesButton.setSpacing(10);
    }

    public String getUsernameText() {
        return username.getText();
    }

    public Button getConnect() {
        return connect;
    }
}
