package com.smartstudy.studyroom;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JMeterReservationConcurrencyPlanTest {

    private static final Path JMETER_PLAN =
            Path.of("jmeter", "study-room-reservation-concurrency.jmx");

    private static final Path CSV_FILE =
            Path.of("jmeter", "reservation-create-users.csv");

    @Test
    void shouldProvideReservationConcurrencyJMeterPlan()
            throws Exception {

        assertThat(Files.exists(JMETER_PLAN))
                .isTrue();

        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl",
                true
        );

        Document document = factory.newDocumentBuilder()
                .parse(JMETER_PLAN.toFile());

        assertThat(document.getDocumentElement().getNodeName())
                .isEqualTo("jmeterTestPlan");
    }

    @Test
    void shouldTargetCreateReservationApiWithServerControlledSlots()
            throws Exception {

        String plan = Files.readString(JMETER_PLAN);

        assertThat(plan)
                .contains("/api/reservation/create");
        assertThat(plan)
                .contains("Authorization");
        assertThat(plan)
                .contains("Bearer ${token}");
        assertThat(plan)
                .contains("&quot;requestId&quot;: &quot;${__UUID()}&quot;");
        assertThat(plan)
                .contains("&quot;seatId&quot;: ${seatId}");
        assertThat(plan)
                .contains("&quot;roomId&quot;: ${roomId}");
        assertThat(plan)
                .contains("&quot;reservationDate&quot;: &quot;${reservationDate}&quot;");
        assertThat(plan)
                .contains("&quot;startSlotId&quot;: ${startSlotId}");
        assertThat(plan)
                .contains("&quot;endSlotId&quot;: ${endSlotId}");
    }

    @Test
    void shouldReadReservationParametersFromCsvWithoutRealToken()
            throws Exception {

        assertThat(Files.exists(CSV_FILE))
                .isTrue();

        List<String> lines = Files.readAllLines(CSV_FILE);

        assertThat(lines)
                .isNotEmpty();
        assertThat(lines.get(0))
                .isEqualTo("token,roomId,seatId,reservationDate,startSlotId,endSlotId");
        assertThat(String.join("\n", lines))
                .contains("REPLACE_WITH_LOGIN_TOKEN")
                .doesNotContain("st.");
    }

    @Test
    void shouldExposeThreadCountAndTargetServerAsRuntimeProperties()
            throws Exception {

        String plan = Files.readString(JMETER_PLAN);

        assertThat(plan)
                .contains("${__P(protocol,http)}")
                .contains("${__P(host,localhost)}")
                .contains("${__P(port,8080)}")
                .contains("${__P(threads,20)}")
                .contains("${__P(ramp_seconds,1)}")
                .contains("${__P(loop_count,1)}");
    }
}
