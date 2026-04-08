/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package banco_hexagonal.pruebatecnica.infraestructure.Adapter.Web;

import banco_hexagonal.pruebatecnica.application.Port.In.ReportUseCase;
import banco_hexagonal.pruebatecnica.domain.Model.Account;
import banco_hexagonal.pruebatecnica.domain.Model.Movement;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author Trabajo
 */
@RestController
@RequestMapping("/reportes")
public class ReportController {
     private final ReportUseCase reportUseCase;
    public ReportController(ReportUseCase reportUseCase){
        this.reportUseCase = reportUseCase;
    }
    
    @GetMapping
    public ResponseEntity<Map<Account, List<Movement>>> generateReport(@RequestParam("clientId") Long clientId,
                                                                        @RequestParam("fecha") String date){
        List<String> dates = Arrays.asList(date.split(","));
         LocalDate from = LocalDate.parse(dates.get(0));
        LocalDate to = LocalDate.parse(dates.get(1));
        Map<Account, List<Movement>> report = reportUseCase.generateReport(clientId, from, to);
        return ResponseEntity.ok(report);
    }
}
