package com.torqmind.ops.domain.company;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "drive_folder_id")
    private String driveFolderId;

    @Column(name = "legal_name")
    private String legalName;

    @Column(name = "cnpj")
    private String cnpj;

    @Embedded
    private PostalAddress address = new PostalAddress();

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDriveFolderId() {
        return driveFolderId;
    }

    public void setDriveFolderId(String driveFolderId) {
        this.driveFolderId = driveFolderId;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getCnpj() {
        return cnpj;
    }

    public void setCnpj(String cnpj) {
        this.cnpj = cnpj;
    }

    public PostalAddress getAddress() {
        if (address == null) {
            address = new PostalAddress();
        }
        return address;
    }

    public void setAddress(PostalAddress address) {
        this.address = address == null ? new PostalAddress() : address;
    }
}
