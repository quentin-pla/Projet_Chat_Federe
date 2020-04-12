import controllers.ClientController;
import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        //Titre de la fenêtre
        primaryStage.setTitle("ChatamuCentral");
        //Fenêtre non redimensionable
        primaryStage.setResizable(false);
        //Initialisation du controleur
        ClientController controller = new ClientController(primaryStage);
        //Récupération de la scène du controleur
        primaryStage.setScene(controller.getScene());//controller.getScene()
        //Affichage de la fenêtre
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
