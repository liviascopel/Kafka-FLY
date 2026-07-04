package br.ufes.soe.domain.ranking;

import java.io.Serializable;

public record AirlineMetrics(
    String airlineName,
    long totalFlights,
    long delayedFlights,
    long cancelledFlights,
    double score
) implements Serializable {

    // inicializador para quando a companhia aérea aparecer pela primeira vez na janela
    public static AirlineMetrics empty(String airlineName) {
        return new AirlineMetrics(airlineName, 0, 0, 0, 100.0);
    }

    // somar um novo voo ao total
    public AirlineMetrics increment(String status, int delayMinutes) {
        long newTotal = this.totalFlights + 1;
        long newCancelled = this.cancelledFlights;
        long newDelayed = this.delayedFlights;

        // regra de negocio
        if ("cancelled".equalsIgnoreCase(status)) {
            newCancelled++;
        } else if ("incident".equalsIgnoreCase(status) || delayMinutes > 1) { 
            // atrasado se a aviationstack marcar ou se o delay passar de 15 min
            newDelayed++;
        }

        // algoritmo da nota: cancelamento tem peso 0.5 e atraso tem peso 1
        double newScore = 100.0;
        if (newTotal > 0) {
            double penalties = (newDelayed * 1.0) + (newCancelled * 0.5);
            newScore = ((newTotal - penalties) / newTotal) * 100.0;
            // garante que nao fica negativo
            if (newScore < 0) newScore = 0.0; 
        }

        return new AirlineMetrics(this.airlineName, newTotal, newDelayed, newCancelled, newScore);
    }
}