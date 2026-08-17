package com.torqmind.ops.domain.company;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class PostalAddress {

    @Column(name = "address_street")
    private String street;

    @Column(name = "address_number")
    private String number;

    @Column(name = "address_complement")
    private String complement;

    @Column(name = "address_neighborhood")
    private String neighborhood;

    @Column(name = "address_city")
    private String city;

    @Column(name = "address_state")
    private String state;

    @Column(name = "address_postal_code")
    private String postalCode;

    public boolean isBlank() {
        return blank(street) && blank(number) && blank(complement) && blank(neighborhood)
                && blank(city) && blank(state) && blank(postalCode);
    }

    public PostalAddress copy() {
        PostalAddress copy = new PostalAddress();
        copy.street = street;
        copy.number = number;
        copy.complement = complement;
        copy.neighborhood = neighborhood;
        copy.city = city;
        copy.state = state;
        copy.postalCode = postalCode;
        return copy;
    }

    public String formatted() {
        StringBuilder sb = new StringBuilder();
        append(sb, street);
        if (!blank(number)) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(number);
        }
        if (!blank(complement)) {
            if (sb.length() > 0) {
                sb.append(" — ");
            }
            sb.append(complement);
        }
        if (!blank(neighborhood)) {
            if (sb.length() > 0) {
                sb.append(" — ");
            }
            sb.append(neighborhood);
        }
        if (!blank(city) || !blank(state)) {
            if (sb.length() > 0) {
                sb.append(" — ");
            }
            if (!blank(city)) {
                sb.append(city);
            }
            if (!blank(state)) {
                if (!blank(city)) {
                    sb.append('/');
                }
                sb.append(state);
            }
        }
        if (!blank(postalCode)) {
            if (sb.length() > 0) {
                sb.append(" — CEP ");
            } else {
                sb.append("CEP ");
            }
            sb.append(postalCode);
        }
        return sb.toString();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void append(StringBuilder sb, String value) {
        if (!blank(value)) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(value.trim());
        }
    }

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getComplement() { return complement; }
    public void setComplement(String complement) { this.complement = complement; }
    public String getNeighborhood() { return neighborhood; }
    public void setNeighborhood(String neighborhood) { this.neighborhood = neighborhood; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
}
