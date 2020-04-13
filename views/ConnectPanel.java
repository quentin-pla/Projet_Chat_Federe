package views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/**
 * Page d'accueil
 */
public class ConnectPanel extends BorderPane {
    private TextField       ip = new TextField("localhost");
    private TextField     port = new TextField("12345");
    private Button     connect = new Button("Connexion");
    private Label errorMessage = new Label();
    private ImageView  logoamu = new ImageView(new Image("/amu-logo.png"));

    public ConnectPanel() {
        setMinSize(500, 300);
        connect.setMinWidth(150);
        ip.setMaxWidth(150);
        ip.setPromptText("Adresse IP");
        ip.setDisable(true);
        port.setMaxWidth(150);
        port.setPromptText("Port");
        errorMessage.setTextFill(Color.RED);
        VBox gamesButton = new VBox(logoamu, ip, port, connect, errorMessage);
        VBox.setMargin(logoamu, new Insets(10));
        BorderPane.setAlignment(gamesButton, Pos.CENTER);
        setCenter(gamesButton);
        gamesButton.setAlignment(Pos.CENTER);
        gamesButton.setSpacing(10);
    }

    public String getIpText() {
        return ip.getText();
    }

    public String getPortText() {
        return port.getText();
    }

    public Button getConnect() {
        return connect;
    }

    public void showError(String message) {
        errorMessage.setText(message);
    }
}
