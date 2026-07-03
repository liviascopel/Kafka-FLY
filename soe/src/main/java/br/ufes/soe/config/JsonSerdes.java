package br.ufes.soe.config;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import br.ufes.soe.domain.flight.Flight;
import br.ufes.soe.domain.weather.AirportWeather;
import br.ufes.soe.domain.weather.WeatherAlert;

public class JsonSerdes {

    public static Serde<Flight> flight() {
        JacksonJsonSerializer<Flight> serializer = new JacksonJsonSerializer<>();
        JacksonJsonDeserializer<Flight> deserializer = new JacksonJsonDeserializer<>(Flight.class);
        deserializer.setUseTypeHeaders(false);
        return Serdes.serdeFrom(serializer, deserializer);
    }

    public static Serde<AirportWeather> airportWeather() {
        JacksonJsonSerializer<AirportWeather> serializer = new JacksonJsonSerializer<>();
        JacksonJsonDeserializer<AirportWeather> deserializer = new JacksonJsonDeserializer<>(AirportWeather.class);
        deserializer.setUseTypeHeaders(false);
        return Serdes.serdeFrom(serializer, deserializer);
    }

    public static Serde<WeatherAlert> weatherAlert() {
        JacksonJsonSerializer<WeatherAlert> serializer = new JacksonJsonSerializer<>();
        JacksonJsonDeserializer<WeatherAlert> deserializer = new JacksonJsonDeserializer<>(WeatherAlert.class);
        deserializer.setUseTypeHeaders(false);
        return Serdes.serdeFrom(serializer, deserializer);
    }
}