package views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Page d'accueil
 */
public class LoginPanel extends BorderPane {
    private Label  identifiant = new Label("Identifiant");
    private TextField username = new TextField();
    private Button     connect = new Button("Valider");
    private Label errorMessage = new Label();

    public LoginPanel() {
        setMinSize(500, 300);
        connect.setMinWidth(150);
        username.setMaxWidth(150);
        username.setPromptText("Identifiant");
        errorMessage.setTextFill(Color.RED);
        identifiant.setStyle("-fx-font-size: 30");
        VBox gamesButton = new VBox(identifiant, username, connect, errorMessage);
        VBox.setMargin(identifiant, new Insets(10));
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

    public void showError(String message) {
        errorMessage.setText(message);
    }
}
