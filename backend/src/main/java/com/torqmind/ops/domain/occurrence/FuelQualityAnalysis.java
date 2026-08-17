package com.torqmind.ops.domain.occurrence;

import com.torqmind.ops.domain.company.PostalAddress;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "fuel_quality_analyses")
public class FuelQualityAnalysis {

    @Id
    @Column(name = "occurrence_id")
    private Long occurrenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fuel", nullable = false)
    private FuelKind fuel;

    @Column(name = "station_name")
    private String stationName;

    @Column(name = "station_legal_name")
    private String stationLegalName;

    @Column(name = "station_cnpj")
    private String stationCnpj;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "street", column = @Column(name = "station_address_street")),
            @AttributeOverride(name = "number", column = @Column(name = "station_address_number")),
            @AttributeOverride(name = "complement", column = @Column(name = "station_address_complement")),
            @AttributeOverride(name = "neighborhood", column = @Column(name = "station_address_neighborhood")),
            @AttributeOverride(name = "city", column = @Column(name = "station_address_city")),
            @AttributeOverride(name = "state", column = @Column(name = "station_address_state")),
            @AttributeOverride(name = "postalCode", column = @Column(name = "station_address_postal_code"))
    })
    private PostalAddress stationAddress = new PostalAddress();

    @Column(name = "collection_date")
    private LocalDate collectionDate;

    @Column(name = "received_volume")
    private String receivedVolume;

    @Column(name = "distributor_name")
    private String distributorName;

    @Column(name = "distributor_cnpj")
    private String distributorCnpj;

    @Column(name = "transporter")
    private String transporter;

    @Column(name = "product_nfe")
    private String productNfe;

    @Column(name = "truck_plate")
    private String truckPlate;

    @Column(name = "trailer_plate")
    private String trailerPlate;

    @Column(name = "driver_name")
    private String driverName;

    @Column(name = "driver_document")
    private String driverDocument;

    @Column(name = "analyst_name")
    private String analystName;

    @Column(name = "appearance")
    private String appearance;

    @Column(name = "color")
    private String color;

    @Column(name = "specific_mass_20c")
    private String specificMass20c;

    @Column(name = "gasoline_alcohol_content")
    private String gasolineAlcoholContent;

    @Column(name = "aehc_alcohol_content")
    private String aehcAlcoholContent;

    @Column(name = "filled_by_name")
    private String filledByName;

    @Column(name = "filled_by_user_id")
    private UUID filledByUserId;

    @Column(name = "responsible_signature_attachment_id")
    private Long responsibleSignatureAttachmentId;

    @Column(name = "witnesses_json")
    private String witnessesJson;

    public Long getOccurrenceId() { return occurrenceId; }
    public void setOccurrenceId(Long occurrenceId) { this.occurrenceId = occurrenceId; }
    public FuelKind getFuel() { return fuel; }
    public void setFuel(FuelKind fuel) { this.fuel = fuel; }
    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }
    public String getStationLegalName() { return stationLegalName; }
    public void setStationLegalName(String stationLegalName) { this.stationLegalName = stationLegalName; }
    public String getStationCnpj() { return stationCnpj; }
    public void setStationCnpj(String stationCnpj) { this.stationCnpj = stationCnpj; }
    public PostalAddress getStationAddress() {
        if (stationAddress == null) {
            stationAddress = new PostalAddress();
        }
        return stationAddress;
    }
    public void setStationAddress(PostalAddress stationAddress) {
        this.stationAddress = stationAddress == null ? new PostalAddress() : stationAddress;
    }
    public LocalDate getCollectionDate() { return collectionDate; }
    public void setCollectionDate(LocalDate collectionDate) { this.collectionDate = collectionDate; }
    public String getReceivedVolume() { return receivedVolume; }
    public void setReceivedVolume(String receivedVolume) { this.receivedVolume = receivedVolume; }
    public String getDistributorName() { return distributorName; }
    public void setDistributorName(String distributorName) { this.distributorName = distributorName; }
    public String getDistributorCnpj() { return distributorCnpj; }
    public void setDistributorCnpj(String distributorCnpj) { this.distributorCnpj = distributorCnpj; }
    public String getTransporter() { return transporter; }
    public void setTransporter(String transporter) { this.transporter = transporter; }
    public String getProductNfe() { return productNfe; }
    public void setProductNfe(String productNfe) { this.productNfe = productNfe; }
    public String getTruckPlate() { return truckPlate; }
    public void setTruckPlate(String truckPlate) { this.truckPlate = truckPlate; }
    public String getTrailerPlate() { return trailerPlate; }
    public void setTrailerPlate(String trailerPlate) { this.trailerPlate = trailerPlate; }
    public String getDriverName() { return driverName; }
    public void setDriverName(String driverName) { this.driverName = driverName; }
    public String getDriverDocument() { return driverDocument; }
    public void setDriverDocument(String driverDocument) { this.driverDocument = driverDocument; }
    public String getAnalystName() { return analystName; }
    public void setAnalystName(String analystName) { this.analystName = analystName; }
    public String getAppearance() { return appearance; }
    public void setAppearance(String appearance) { this.appearance = appearance; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getSpecificMass20c() { return specificMass20c; }
    public void setSpecificMass20c(String specificMass20c) { this.specificMass20c = specificMass20c; }
    public String getGasolineAlcoholContent() { return gasolineAlcoholContent; }
    public void setGasolineAlcoholContent(String gasolineAlcoholContent) { this.gasolineAlcoholContent = gasolineAlcoholContent; }
    public String getAehcAlcoholContent() { return aehcAlcoholContent; }
    public void setAehcAlcoholContent(String aehcAlcoholContent) { this.aehcAlcoholContent = aehcAlcoholContent; }
    public String getFilledByName() { return filledByName; }
    public void setFilledByName(String filledByName) { this.filledByName = filledByName; }
    public UUID getFilledByUserId() { return filledByUserId; }
    public void setFilledByUserId(UUID filledByUserId) { this.filledByUserId = filledByUserId; }
    public Long getResponsibleSignatureAttachmentId() { return responsibleSignatureAttachmentId; }
    public void setResponsibleSignatureAttachmentId(Long responsibleSignatureAttachmentId) {
        this.responsibleSignatureAttachmentId = responsibleSignatureAttachmentId;
    }
    public String getWitnessesJson() { return witnessesJson; }
    public void setWitnessesJson(String witnessesJson) { this.witnessesJson = witnessesJson; }
}
