import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import views.HomePanel;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("ChatamuCentral");
        primaryStage.setResizable(false);

        primaryStage.setScene(new Scene(new HomePanel()));

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
