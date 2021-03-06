/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package interfacecontrollers;

import constants.SensorUnits;
import constants.Settings;
import entities.Reading;
import entities.Sensor;
import constants.StatusType.Status;
import constants.StatusType.Type;
import entitymanagers.ReadingManager;
import entitymanagers.SensorManager;
import java.io.IOException;
import java.net.URL;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.HashMap;
import java.util.function.Consumer;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import threads.SensorThread;
import threads.SensorFactory;
import threads.UpdateThread;
import weatherstation.WeatherStation;

/**
 * FXML Controller class
 *
 * @author harvey
 */
public class MainInterfaceController implements Initializable {

    @FXML
    private ListView<Sensor> humListview;
    @FXML
    private ListView<Sensor> pressListView;
    @FXML
    private ListView<Sensor> tempListView;
    @FXML
    private ListView<Sensor> speedListView;
    @FXML
    private Tab mondayPane;
    @FXML
    private Tab tuesdayPane;
    @FXML
    private Tab wedPane;
    @FXML
    private Tab thursPane;
    @FXML
    private Tab friPane;
    @FXML
    private Tab satPane;
    @FXML
    private Tab sunPane;
    @FXML
    public LineChart<String, Number> lineChart;
    @FXML
    private NumberAxis yAxis;
    @FXML
    private CategoryAxis xAxis;
    @FXML
    private ToggleButton toggleStateButton;
    @FXML
    private DatePicker datePicker;
    @FXML
    private TextField currentValue;

    private final EntityManagerFactory emf;
    private final EntityManager manager;
    private final SensorManager sensorManager;
    public static ReadingManager readingManager;

    private final String addEditPath = "/resources/dialogs/addSensor.fxml";
    private final String settingsPath = "/resources/dialogs/settings.fxml";
    private Stage primaryStage;
    public static final SensorFactory sensorFactory = new SensorFactory();
    private final ExecutorService executorService;
    private ListView<Sensor> currentSelectedList, previousList;
    private final List<UpdateThread> regUpdateSensors;
    private List<Tab> panes;
    private Settings settings;
    public static final Series<String, Number> series = new Series<>();
    @FXML
    private MenuItem settingMButtin;

    /**
     * Initializes the controller class.
     *
     * @param url
     * @param rb
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        primaryStage = WeatherStation.getPrimaryStage();
        primaryStage.setOnCloseRequest((WindowEvent event1) -> {
            exit();
        });
        ListUpdateListener allListListener = new ListUpdateListener();
        //Register listview listeners
        speedListView.getSelectionModel().selectedItemProperty().addListener(allListListener);
        tempListView.getSelectionModel().selectedItemProperty().addListener(allListListener);
        pressListView.getSelectionModel().selectedItemProperty().addListener(allListListener);
        humListview.getSelectionModel().selectedItemProperty().addListener(allListListener);

        xAxis.setLabel("Time");
        lineChart.getData().add(series);

        loadSensor();
        panes = initTabPane();
        datePicker.valueProperty().addListener(
                (ObservableValue<? extends LocalDate> observable, LocalDate oldValue, LocalDate newValue) -> {
                    System.out.println(newValue);
                    LocalDateTime date = newValue.minusDays(6).atStartOfDay();
                    if (currentSelectedList != null) {
                        List<Reading> weekreadings = readingManager.get7DaysSensorReading(
                                currentSelectedList.getSelectionModel().getSelectedItem(),
                                Timestamp.valueOf(date),
                                Timestamp.valueOf(LocalDateTime.of(newValue, LocalTime.MAX)));
                        System.out.println(weekreadings.size());
                        if (weekreadings.isEmpty()) {
                            panes.stream().forEach((pane) -> {
                                ((ScrollPane) pane.getContent()).setContent(null);
                            });
                            return;
                        }
                        showTable(weekreadings);
                    }

                });
//        System.out.println(mondayPane);
    }

    public MainInterfaceController() {
        this.emf = Persistence.createEntityManagerFactory("WeatherStationPU");
        executorService = Executors.newCachedThreadPool();

        this.manager = emf.createEntityManager();
        MainInterfaceController.readingManager = new ReadingManager(manager);
        this.sensorManager = new SensorManager(manager);
        this.regUpdateSensors = new ArrayList<>();
        this.previousList = null;
    }

    @FXML
    private void addSensor() {
        try {
            Sensor sensor = new Sensor();
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(WeatherStation.class.getResource(addEditPath));

            Stage dialogStage = createDialogStage("Create New Sensor", loader);
            AddSensorController controller = loader.getController();
            controller.initSensorController(dialogStage, sensor);
            dialogStage.showAndWait();

            if (controller.okClicked()) {
                //Write to database
                sensor = sensorManager.createSensor(sensor);
                addSensorToList(sensor);
                if (sensor.getStatus().equals(Status.ON.toString())) {
                    //can only be null at application start, which means currentselectedList is null too
                    if (previousList == null) {
                        startSensorThread(sensor, true);
                        setCurrentList(sensor);
                        currentSelectedList.getSelectionModel().select(0);
                    } else {//Is not the first launch, start the thread anyways but dont display
                        startSensorThread(sensor, false);
                    }
                }
                writeInfo("A new sensor " + sensor.getName() + " was created successfully!");
            }
        } catch (IOException ex) {
            catchException(ex);
        }
    }

    private void editSensor(Sensor sensor) {
        //TODO: the update should be general
        try {

            String oldName = sensor.getName();
            System.out.println(sensor.getId());
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(WeatherStation.class.getResource(addEditPath));
            Stage dialogStage = createDialogStage("Edit Sensor", loader);
            AddSensorController controller = loader.getController();
            controller.initSensorController(dialogStage, sensor);
            controller.populateSersorUI();
            dialogStage.showAndWait();

            if (controller.okClicked()) {
                //Write to database
                sensorManager.updateSensor(sensor);
                regUpdateSensors.stream().forEach((UpdateThread updateThread) -> {
                    if (updateThread.getSensor().getId().equals(sensor.getId())) {
                        updateThread.stop();
//                        sensorFactory.getThreads().removeIf(sensor.getId().equals(4));
                    }
                });
                sensorFactory.getThreads().stream().forEach((thread)->{
                    if (thread.getSensor().equals(sensor)) {
                        thread.stop();
                    }
                });
                writeInfo("Sensor name changed successfully edited!");
            }
        } catch (IOException ex) {
            catchException(ex);
        }
    }

    private void exit() {
        try {
            regUpdateSensors.stream().forEach((thread) -> {
                thread.stop();
            });
            sensorFactory.getThreads().stream().forEach((thread) -> {
                thread.stop();
            });
            executorService.shutdown();
            executorService.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Logger.getLogger(MainInterfaceController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @FXML
    private void exitApp() {
        exit();
        Platform.exit();
    }

    @FXML
    private void launchSettings(Event event) {
        try {
            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(WeatherStation.class.getResource(settingsPath));
            Stage dialogStage = createDialogStage("Settings", loader);
            SettingsController controller = loader.getController();
            HashMap<String, String> setting = new HashMap<>();
            controller.settingsController(setting, dialogStage);
            dialogStage.showAndWait();

            if (controller.isOkClicked()) {
                String interval = String.valueOf((Integer.valueOf(setting.get("Hour")) * 3600
                        + Integer.valueOf(setting.get("Minute")) * 60
                        + Integer.valueOf(setting.get("Second"))) * 1000);
                UpdateThread.setUpdateInterval(Integer.parseInt(interval));

                HashMap<String, String> map = new HashMap<>(5);
                map.put("interval", interval);
                Settings.addRecords(map);
            }
        } catch (IOException ex) {
            Logger.getLogger(MainInterfaceController.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @FXML
    private void about(ActionEvent event) {
    }

    @FXML
    private void toggleSensorStatus() {
        Sensor selectedItem = currentSelectedList.getSelectionModel().getSelectedItem();

        if (selectedItem.getStatus().equals(Status.OFF.toString())) {
            //update sensor status
            sensorManager.switchSensorStatus(selectedItem);
            startSensorThread(selectedItem, true);

        } else if (selectedItem.getStatus().equals(Status.ON.toString())) {
            //update sensor status
            sensorManager.switchSensorStatus(selectedItem);
            //stop reading weather value
            stopSensorThread(selectedItem);
        }
    }

    private void loadSensor() {
        List<Sensor> sensors = sensorManager.getSensors();
        System.out.println(sensors.size());
        sensors.stream().forEach((sensor) -> {
            switch (sensor.getType()) {
                case "HUMIDITY":
                    humListview.getItems().add(sensor);
                    break;
                case "PRESSURE":
                    pressListView.getItems().add(sensor);
                    break;
                case "TEMPERATURE":
                    tempListView.getItems().add(sensor);
                    break;
                case "WIND_SPEED":
                    speedListView.getItems().add(sensor);
            }
            startSensorThread(sensor, false);
        });
    }

    private void startSensorThread(Sensor sensor, boolean showOnChart) {
        if (sensor.getStatus().equals(Status.OFF.toString())) {
            return;
        }
        SensorThread sensorThread = sensorFactory.createSensorThread(sensor);
        executorService.execute(sensorThread);
        //start updateThread for this sensor if not already started
        HashMap<String, String> readRecords = Settings.readRecords();
        if (regUpdateSensors.isEmpty()) {//if empty, add first item
            UpdateThread updateThread;
            try {
                updateThread = new UpdateThread(sensor, Integer.parseInt(readRecords.get("interval")), currentValue);//value to change
            } catch (NumberFormatException e) {
                updateThread = new UpdateThread(sensor, 5000, currentValue);
            }
            updateThread.setIsVisibleOnChart(true);
            regUpdateSensors.add(updateThread);
            executorService.execute(updateThread);
        } else {//find the sensorThread for this sensor if it exists
            boolean isIn = false;
            for (UpdateThread thread : regUpdateSensors) {
                if (thread.getSensor().equals(sensor)) {
                    isIn = true;
                    break;
                }
            }
            if (!isIn) {//if its not found in the list create an update thread for it
                UpdateThread updateThread = new UpdateThread(sensor, Integer.parseInt(readRecords.get("interval")), currentValue);//value to change
                updateThread.setIsVisibleOnChart(showOnChart);
                regUpdateSensors.add(updateThread);
                executorService.execute(updateThread);
            }
        }
    }

    public void stopSensorThread(Sensor sensor) {
        sensorFactory.interruptThread(sensor);

        for (UpdateThread thread : regUpdateSensors) {
            if (thread.getSensor().equals(sensor)) {
                //stop updating chart and database
                thread.setIsAlive(false);
                thread.stop();
                this.regUpdateSensors.remove(thread);
                break;
            }
        }
    }

    private void showTable(List<Reading> readings) {
        HashMap<Integer, List<Reading>> weekreading = new HashMap<>();
        for (int i = 0; i < 7; i++) {
            weekreading.put(i, new ArrayList<>());
        }
        readings.stream().forEach((reading) -> {
            Instant instant = Instant.ofEpochMilli(reading.getRegDate().getTime());
            LocalDateTime date = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            //dayofweek from 1 to 7
            weekreading.get(date.getDayOfWeek().getValue() - 1).add(reading);
        });

        final int rowCount = 2;
        for (int i = 0; i < 7; i++) {
            List<Reading> dayReadings = weekreading.get(i);
            GridPane gridP = new GridPane();
            gridP.setAlignment(Pos.CENTER);
            gridP.setGridLinesVisible(true);
            ((ScrollPane) (panes.get(i)
                    .getContent()))
                    .setContent(gridP);
            Label label = new Label("Time");
            label.setPadding(new Insets(10, 10, 10, 10));
            gridP.getChildren().add(label);
            GridPane.setConstraints(label, 0, 0);
            label = new Label("Reading");
            label.setPadding(new Insets(10, 10, 10, 10));
            gridP.getChildren().add(label);
            GridPane.setConstraints(label, 0, 1);
            int count = 1;
            for (int j = 1; j < dayReadings.size(); j++) {
                Instant instant = Instant.ofEpochMilli(dayReadings.get(j).getRegDate().getTime());
                LocalDateTime date = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
                label = new Label(date.getHour() + ":" + date.getMinute() + ":" + date.getSecond());
                label.setPadding(new Insets(10, 10, 10, 10));
                System.out.println(label);
                gridP.getChildren().add(label);
                GridPane.setConstraints(label, j, 0);
                label = new Label(String.valueOf(dayReadings.get(count).getReadValue()));
                System.out.println(label);
                label.setPadding(new Insets(10, 10, 10, 10));
                gridP.getChildren().add(label);
                GridPane.setConstraints(label, j, 1);
                count++;
            }
            gridP.getColumnConstraints().stream().forEach((col) -> {
                col.setMinWidth(100);
                col.setHalignment(HPos.CENTER);
            });
        }
    }

    private List<Tab> initTabPane() {
        final List<Tab> panes = new ArrayList<>();
        panes.add(mondayPane);
        panes.add(tuesdayPane);
        panes.add(wedPane);
        panes.add(thursPane);
        panes.add(friPane);
        panes.add(satPane);
        panes.add(sunPane);
        return panes;
    }

    private Stage createDialogStage(String title, FXMLLoader loader) throws IOException {

        final Stage dialogStage = new Stage();
        dialogStage.setTitle(title);
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.initOwner(primaryStage);
        AnchorPane pane = (AnchorPane) loader.load();
        Scene scene = new Scene(pane);
        dialogStage.setScene(scene);
        dialogStage.resizableProperty().set(false);
        return dialogStage;
    }

    private void catchException(IOException ex) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle("Unexpected Error");
        String s = "An error occured while trying to open the add dialog!";
        alert.setContentText(s);
        alert.showAndWait();

        Logger.getLogger(MainInterfaceController.class.getName()).log(Level.SEVERE, null, ex);
    }

    private void writeInfo(String message) {
        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Request Completed!");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void addSensorToList(Sensor sensor) {
        switch (sensor.getType()) {
            case "HUMIDITY":
                humListview.getItems().add(sensor);
                break;
            case "PRESSURE":
                pressListView.getItems().add(sensor);
                break;
            case "TEMPERATURE":
                tempListView.getItems().add(sensor);
                break;
            case "WIND_SPEED":
                speedListView.getItems().add(sensor);
        }
    }

    private void setCurrentList(Sensor sensor) {
        HashMap<String, String> readRecords = Settings.readRecords();
        switch (Type.valueOf(sensor.getType())) {
            case HUMIDITY:
                currentSelectedList = humListview;
                yAxis.setLabel("Humidity(%)");
                break;
            case PRESSURE:
                currentSelectedList = pressListView;
                yAxis.setLabel("Pressure(mb)");
                break;
            case TEMPERATURE:
                currentSelectedList = tempListView;
                yAxis.setLabel("Temperature(c)");
                break;
            default:
                currentSelectedList = speedListView;
                yAxis.setLabel("Wind(mps)");
        }
    }

    @FXML
    private void showContextMenu(ContextMenuEvent event) {
        final List<Sensor> selectedItems = ((ListView<Sensor>) event.getSource()).getSelectionModel().getSelectedItems();
        if (selectedItems.isEmpty()) {
            return;
        }
        MenuItem edit = new MenuItem("Edit sensor");
        MenuItem remove = new MenuItem("Delete");
        ContextMenu contextMenu = new ContextMenu();
        if (selectedItems.size() == 1) {
            contextMenu.getItems().add(edit);
        }
        contextMenu.getItems().add(remove);
        contextMenu.show((Node) event.getSource(), event.getScreenX(), event.getScreenY());

        edit.setOnAction((ActionEvent event1) -> {
            ObservableList<Sensor> items = ((ListView<Sensor>) event.getSource()).getItems();
            int indexOfItem = items.indexOf(selectedItems.get(0));
            editSensor(selectedItems.get(0));
            Sensor sensor = sensorManager.findByName(selectedItems.get(0).getName());
            items.set(indexOfItem, sensor);
        });
        remove.setOnAction((ActionEvent e) -> {
            selectedItems.stream().forEach(((ListView<Sensor>) event.getSource()).getItems()::remove);
        });
    }

    public void populateGraph(Sensor sensor, LocalDateTime datetime, List<Reading> readings) {
        //defining the XAxis 
//        readings.stream().forEach((read) -> {
//            System.out.print("[" + read.getId() + ":" + read.getReadValue() + "], ");
//        });
//        System.out.println("");

        lineChart.setTitle(sensor.getName() + " Readings of " + String.format("%d/%d/%d",
                datetime.getDayOfMonth(), datetime.getMonthValue(), datetime.getYear()));

        series.getData().clear();
        readings.stream().forEach((reading) -> {
            Instant ins = Instant.ofEpochMilli(reading.getRegDate().getTime());
            LocalDateTime datet = LocalDateTime.ofInstant(ins, ZoneId.systemDefault());
            series.getData().add(new XYChart.Data(String.format("%2d:%2d:%2d", datet.getHour(),
                    datet.getMinute(), datet.getSecond()), reading.getReadValue()));
        });
    }

    public class ListUpdateListener implements ChangeListener<Sensor> {

        @Override
        public void changed(ObservableValue<? extends Sensor> observable, Sensor oldValue, Sensor newValue) {
            if (toggleStateButton.isDisabled()) {
                toggleStateButton.setDisable(false);
            }
            if (settingMButtin.disableProperty().get()) {
                settingMButtin.disableProperty().set(false);
            }

            //if same values in same list, exit
            if (oldValue == newValue || newValue == null) {
                return;
            }
            if (oldValue != null) {
                System.out.print("old: " + oldValue.getName());

            }
            System.out.println(" new: " + newValue.getName());
            toggleStateButton.setSelected(newValue.getStatus().equals(Status.ON.toString()));
            setCurrentList(newValue);

            if (!currentSelectedList.equals(previousList)) {
                if (previousList != null) {
                    regUpdateSensors.stream().forEach((thread) -> {
                        if (thread.getSensor().getName().equals(
                                previousList.getSelectionModel().getSelectedItem().getName())) {
                            thread.setIsVisibleOnChart(false);
                        } else if (thread.getSensor().getName().equals(newValue.getName())) {
                            thread.setIsVisibleOnChart(true);
                        }
                    });
                    previousList.getSelectionModel().clearSelection();
                }

            } else {
                regUpdateSensors.stream().forEach((thread) -> {
                    if (thread.getSensor().equals(oldValue)) {
                        thread.setIsVisibleOnChart(false);
                    } else if (thread.getSensor().getName().equals(newValue.getName())) {
                        thread.setIsVisibleOnChart(true);
                    }
                });
            }
            previousList = currentSelectedList;

            /*
             Read the current sensor's data for today from the database
             Display it in the graph
             Display it under the tabPane approprate table and make that tab selected
             */
//            read current sensor value(s)for today from database
            List<Reading> todaysReadings = readingManager.getTodaysReadings(newValue);
            populateGraph(newValue, LocalDateTime.now(), todaysReadings);
        }
    }
}
