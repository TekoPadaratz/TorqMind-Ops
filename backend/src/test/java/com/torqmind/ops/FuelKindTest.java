package com.torqmind.ops;

import com.torqmind.ops.domain.occurrence.FuelKind;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FuelKindTest {

    @Test
    void gasolineShowsAlcoholDieselDoesNot() {
        Assertions.assertTrue(FuelKind.GASOLINA_COMUM.showsGasolineAlcohol());
        Assertions.assertTrue(FuelKind.GASOLINA_ADITIVADA.showsGasolineAlcohol());
        Assertions.assertTrue(FuelKind.ETANOL.showsAehcAlcohol());
        Assertions.assertFalse(FuelKind.DIESEL_S10.showsGasolineAlcohol());
        Assertions.assertFalse(FuelKind.DIESEL_S10.showsAehcAlcohol());
        Assertions.assertFalse(FuelKind.DIESEL_S500.showsAehcAlcohol());
    }

    @Test
    void fromSpeechSelectsFuelWhenSpoken() {
        Assertions.assertEquals(FuelKind.GASOLINA_COMUM, FuelKind.fromSpeech("abrir análise de qualidade da gasolina comum"));
        Assertions.assertEquals(FuelKind.DIESEL_S500, FuelKind.fromSpeech("registrar análise de combustível diesel S-500"));
        Assertions.assertEquals(FuelKind.ETANOL, FuelKind.fromSpeech("abrir análise de qualidade do etanol"));
    }
}
