/* 
 * Copyright 2015 Adobe.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.adobe.acs.epic.controller;

import com.adobe.acs.epic.ApplicationState;
import com.adobe.acs.epic.DataUtils;
import com.adobe.acs.epic.EpicApp;
import com.adobe.acs.epic.PackageOps;
import com.adobe.acs.epic.model.CrxPackage;
import com.adobe.acs.epic.model.PackageComparison;
import com.adobe.acs.model.pkglist.CrxType;
import com.adobe.acs.model.pkglist.PackageType;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.FileChooser;
import javax.xml.bind.JAXB;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;

public class AppController {

    Map<String, StringProperty> defaults;
    AuthHandler loginHandler;

    @FXML // ResourceBundle that was given to the FXMLLoader
    private ResourceBundle resources;

    @FXML // URL location of the FXML file that was given to the FXMLLoader
    private URL location;

    @FXML // fx:id="connectionTab"
    private Tab connectionTab; // Value injected by FXMLLoader

    @FXML // fx:id="passwordField"
    private PasswordField passwordField; // Value injected by FXMLLoader

    @FXML // fx:id="hostField"
    private TextField hostField; // Value injected by FXMLLoader

    @FXML // fx:id="usernameField"
    private TextField usernameField; // Value injected by FXMLLoader

    @FXML // fx:id="sslCheckbox"
    private CheckBox sslCheckbox; // Value injected by FXMLLoader

    @FXML // fx:id="connectionVerificationLabel"
    private Label connectionVerificationLabel; // Value injected by FXMLLoader

    @FXML
    private CheckBox showCustomPackages;

    @FXML
    private CheckBox showProductPackages;

    @FXML
    private CheckBox showUninstalledPackages;

    @FXML
    private TableView<PackageType> packageTable;

    @FXML
    private MenuItem compareSelectedMenuItem;

    ObservableList packageList = FXCollections.observableArrayList();

    @FXML // This method is called by the FXMLLoader when initialization is complete
    void initialize() {
        assert connectionTab != null : "fx:id=\"connectionTab\" was not injected: check your FXML file 'App.fxml'.";
        assert passwordField != null : "fx:id=\"passwordField\" was not injected: check your FXML file 'App.fxml'.";
        assert hostField != null : "fx:id=\"hostField\" was not injected: check your FXML file 'App.fxml'.";
        assert usernameField != null : "fx:id=\"usernameField\" was not injected: check your FXML file 'App.fxml'.";
        assert sslCheckbox != null : "fx:id=\"sslCheckbox\" was not injected: check your FXML file 'App.fxml'.";
        assert connectionVerificationLabel != null : "fx:id=\"connectionVerificationLabel\" was not injected: check your FXML file 'App.fxml'.";
        assert packageTable != null : "fx:id=\"packageTable\" was not injected: check your FXML file 'App.fxml'.";
        assert showCustomPackages != null : "fx:id=\"showCustomPackages\" was not injected: check your FXML file 'App.fxml'.";
        assert showProductPackages != null : "fx:id=\"showProductPackages\" was not injected: check your FXML file 'App.fxml'.";
        assert showUninstalledPackages != null : "fx:id=\"showUninstalledPackages\" was not injected: check your FXML file 'App.fxml'.";
        assert compareSelectedMenuItem != null : "fx:id=\"compareSelectedMenuItem\" was not injected: check your FXML file 'App.fxml'.";

        loginHandler = new AuthHandler(
                hostField.textProperty(), sslCheckbox.selectedProperty(),
                usernameField.textProperty(), passwordField.textProperty());

        ApplicationState.getInstance().setAuthHandler(loginHandler);
        connectionVerificationLabel.textProperty().bind(loginHandler.model.statusMessageProperty());
        loginHandler.model.loginConfirmedProperty().addListener((confirmedValue, oldValue, newValue) -> this.updateConnectionTabStyle());
        loginHandler.model.loginConfirmedProperty().addListener((confirmedValue, oldValue, newValue) -> this.readPackagesOnce());
        updateConnectionTabStyle();

        packageTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        packageTable.getSelectionModel().getSelectedItems().addListener((ListChangeListener.Change<? extends PackageType> c) -> {
            compareSelectedMenuItem.setDisable(packageTable.getSelectionModel().getSelectedItems().size() < 2);
        });
        packageTable.setItems(packageList);
        packageTable.getColumns().get(0).setCellValueFactory(new PropertyValueFactory<>("group"));
        packageTable.getColumns().get(1).setCellValueFactory(new PropertyValueFactory<>("name"));
        packageTable.getColumns().get(2).setCellValueFactory(new PropertyValueFactory<>("size"));
        packageTable.getColumns().get(3).setCellValueFactory(cell -> {
            return new ReadOnlyObjectWrapper(PackageOps.getInformativeVersion(cell.getValue()));
        });
        packageTable.getColumns().get(4).setCellValueFactory(new PropertyValueFactory<>("lastModified"));
        packageTable.getColumns().get(4).setComparator(DataUtils::compareDates);
        packageTable.getColumns().get(5).setCellValueFactory(new PropertyValueFactory<>("lastUnpacked"));
        packageTable.getColumns().get(5).setComparator(DataUtils::compareDates);
        packageTable.setOnMouseClicked(this::openProcessInfoDialog);

        showCustomPackages.selectedProperty().addListener(this::reapplyFiltersTriggered);
        showProductPackages.selectedProperty().addListener(this::reapplyFiltersTriggered);
        showUninstalledPackages.selectedProperty().addListener(this::reapplyFiltersTriggered);
    }

    public void exportPackagesButtonPressed(ActionEvent evt) throws FileNotFoundException, IOException {
        FileChooser saveChooser = new FileChooser();
        saveChooser.setTitle("Export (Save) data");
        saveChooser.setInitialFileName("package_list.xlsx");
        File saveFile = saveChooser.showSaveDialog(null);
        if (saveFile != null) {
            FileOutputStream out = new FileOutputStream(saveFile);
            String[] headers = new String[]{
                "Group", "Package Name", "Package File", "Size", "Version",
                "Created", "Created By",
                "Last Modified", "Modified By",
                "Last Unpacked", "Unpacked By"
            };
            DataUtils.exportSpreadsheet(out, packageList, headers,
                    PackageType::getGroup, PackageType::getName,
                    PackageType::getDownloadName, PackageType::getSize,
                    PackageOps::getInformativeVersion,
                    PackageType::getCreated, PackageType::getCreatedBy,
                    PackageType::getLastModified, PackageType::getLastModifiedBy,
                    PackageType::getLastUnpacked, PackageType::getLastUnpackedBy
            );
            out.close();
        }
    }

    private void openProcessInfoDialog(MouseEvent evt) {
        if (evt.getButton() == MouseButton.PRIMARY && evt.getClickCount() == 2) {
            PackageType pkg = packageTable.getSelectionModel().getSelectedItem();
            if (pkg != null) {
                EpicApp.openPackageDetails(pkg);
            }
        }
    }

    private void updateConnectionTabStyle() {
        connectionTab.setStyle("-fx-background-color:" + (loginHandler.model.loginConfirmedProperty().getValue() ? "#8f8" : "#f88"));
    }

    private void readPackagesOnce() {
        if (loginHandler.model.loginConfirmedProperty().getValue() && packageList.isEmpty()) {
            readPackages(null);
        }
    }

    @FXML
    private void readPackages(ActionEvent evt) {
        Platform.runLater(packageList::clear);
        new Thread(() -> {
            try (CloseableHttpClient client = loginHandler.getAuthenticatedClient()) {
                String url = loginHandler.getUrlBase() + "/crx/packmgr/service.jsp?cmd=ls";
                HttpGet request = new HttpGet(url);
                try (CloseableHttpResponse response = client.execute(request)) {
                    CrxType result = JAXB.unmarshal(response.getEntity().getContent(), CrxType.class);
                    List<PackageType> rawList = result.getResponse().getData().getPackages().getPackage();
                    ApplicationState.getInstance().prepareMasterList(rawList);
                    Platform.runLater(this::applyFilters);
                }
            } catch (IOException ex) {
                Logger.getLogger(AppController.class.getName()).log(Level.SEVERE, null, ex);
            }
        }).start();
    }

    Set<Function<CrxPackage, Boolean>> filters = new HashSet<>();

    private void reapplyFiltersTriggered(ObservableValue<? extends Boolean> val, Boolean old, Boolean checked) {
        filters.clear();
        if (!showProductPackages.isSelected()) {
            if (!showCustomPackages.isSelected()) {
                filters.add(p -> false);
            } else {
                filters.add(p -> !PackageOps.isProduct(p));
            }
        } else if (!showCustomPackages.isSelected()) {
            filters.add(PackageOps::isProduct);
        }
        if (!showUninstalledPackages.isSelected()) {
            filters.add(PackageOps::isInstalled);
        }
        applyFilters();
    }

    private void applyFilters() {
        if (ApplicationState.getInstance().getMasterList() != null) {
            packageList.setAll(
                    ApplicationState.getInstance().getMasterList().stream()
                            .filter(pkg -> filters.stream().allMatch(f -> f.apply(pkg)))
                            .collect(Collectors.toList())
            );
        }
    }

    @FXML
    private void compareAll(ActionEvent evt) {
        performMasterComparison(packageList);
    }

    @FXML
    private void compareSelected(ActionEvent evt) {
        performMasterComparison(packageTable.getSelectionModel().getSelectedItems());
    }

    private void performMasterComparison(List<PackageType> pkgs) {
        PackageComparison compare = new PackageComparison();
        DoubleProperty[] downloadProgress = new DoubleProperty[pkgs.size()];
        DoubleBinding overallProgress = null;
        for (int i = 0; i < pkgs.size(); i++) {
            downloadProgress[i] = new SimpleDoubleProperty(PackageOps.hasPackageContents(pkgs.get(i)) ? 1.0 : 0.0);
            if (i == 1) {
                overallProgress = downloadProgress[0].add(downloadProgress[1]);
            } else if (i > 1) {
                overallProgress = overallProgress.add(downloadProgress[i]);
            }
        }
        overallProgress = overallProgress.multiply(1.0 / pkgs.size());

        Alert alert = new Alert(AlertType.INFORMATION);
        alert.setTitle("Preparing report");
        alert.setHeaderText("Downloads in progress");
        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(200, 200);
        progressIndicator.progressProperty().bind(overallProgress);
        alert.getDialogPane().setContent(progressIndicator);
        alert.show();

        new Thread(() -> {
            for (int i = 0; i < pkgs.size(); i++) {
                try {
                    compare.observe(PackageOps.getPackageContents(pkgs.get(i), downloadProgress[i]));
                } catch (IOException ex) {
                    final PackageType pkg = pkgs.get(i);
                    Logger.getLogger(AppController.class.getName()).log(Level.SEVERE, "Error downloading package "+pkg.getName(), ex);
                    Platform.runLater(() -> {
                        Alert error = new Alert(AlertType.ERROR);
                        error.setTitle("Download error");
                        error.setHeaderText("Download error");
                        error.setContentText("Error downloading " + pkg.getName() + " : " + ex.getMessage());
                        error.show();
                    });
                }
            }
            Platform.runLater(() -> {
                alert.close();
                FileChooser saveChooser = new FileChooser();
                saveChooser.setTitle("Export (Save) data");
                saveChooser.setInitialFileName("comparison.xlsx");
                File saveFile = saveChooser.showSaveDialog(null);
                if (saveFile != null) {
                    try {
                        compare.exportMasterReport(saveFile);
                    } catch (IOException ex) {
                        Logger.getLogger(AppController.class.getName()).log(Level.SEVERE, null, ex);
                        Platform.runLater(() -> {
                            Alert error = new Alert(AlertType.ERROR);
                            error.setTitle("Report generation error");
                            error.setHeaderText("Report generation error");
                            error.setContentText(ex.getMessage());
                            error.show();
                        });
                    }
                }
            });
        }).start();
    }
}
