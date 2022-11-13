package lk.ijse.dep9.dto;

import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

/*@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString*/

@Data
@NoArgsConstructor
@AllArgsConstructor

public class TransactionDTO implements Serializable {
    private String type;
    private String account;
    private BigDecimal amount;

}
