package lk.ijse.dep9.api;

import jakarta.annotation.Resource;
import jakarta.json.Json;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import jakarta.json.stream.JsonParser;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.servlet.annotation.*;
import lk.ijse.dep9.dto.AccountDTO;
import lk.ijse.dep9.dto.TransferDTO;
import lk.ijse.dep9.dto.TransactionDTO;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.sql.*;
import java.util.Date;
import java.util.jar.JarException;

@WebServlet(name = "TransactionServlet", value = "/transactions/*", loadOnStartup = 0)
public class TransactionServlet extends HttpServlet {

    @Resource(lookup = "java:/comp/env/jdbc/dep9-boc")
    private DataSource pool;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if(request.getPathInfo()==null || request.getPathInfo().equals("/")){
            try {
                if(request.getContentType()==null || !request.getContentType().startsWith("application/json")){
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,"Invalid JSON");
                    return;
                }

                String json = request.getReader().lines().reduce("", (prev, crnt) -> prev + crnt);

                JsonParser parser = Json.createParser(new StringReader(json));
                parser.next();
                JsonObject jsonObject = parser.getObject();

                String transactionType = jsonObject.getString("type");

                if(transactionType.equalsIgnoreCase("withdraw")){
                    TransactionDTO withdrawDTO = JsonbBuilder.create().fromJson(json, TransactionDTO.class);
                    withdrawMoney(withdrawDTO, response);

                } else if (transactionType.equalsIgnoreCase("deposit")) {
                    TransactionDTO transactionDTO = JsonbBuilder.create().fromJson(json, TransactionDTO.class);
                    depositMoney(transactionDTO, response);

                } else if (transactionType.equalsIgnoreCase("transfer")) {
                    TransferDTO transferDTO = JsonbBuilder.create().fromJson(json, TransferDTO.class);
                    transferMoney(transferDTO, response);

                }else{
                    throw new JsonException("Invalid JSON");
                }

            } catch (JsonException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,"Invalid Valid");
            }

        }else{
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
        }
    }

    private void depositMoney(TransactionDTO transactionDTO, HttpServletResponse response) throws IOException {
        try {
            if(transactionDTO.getAccount() == null || !transactionDTO.getAccount().matches("[A-Za-z0-9-]+")){
                throw new JsonException("Invalid account number");
            } else if (transactionDTO.getAmount() == null ||
                    transactionDTO.getAmount().compareTo(new BigDecimal(100)) < 0) {
                throw new JsonException("Invalid amount");
            }

            Connection connection = pool.getConnection();
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM Account WHERE account_number=?");
            stm.setString(1, transactionDTO.getAccount());
            ResultSet rst = stm.executeQuery();

            if (!rst.next()) throw new JsonException("Invalid account number");

            /* Initiate a transaction */
            try{
                connection.setAutoCommit(false);
                PreparedStatement stmUpdate = connection.prepareStatement
                        ("UPDATE Account SET balance = balance + ? WHERE account_number = ?");

                stmUpdate.setBigDecimal(1, transactionDTO.getAmount());
                stmUpdate.setString(2, transactionDTO.getAccount());
                if(stmUpdate.executeUpdate() != 1) throw new SQLException("Fail to update the balance");

                PreparedStatement stmNewTransaction = connection.prepareStatement
                        ("INSERT INTO Transaction (account, type, description, amount, date) VALUES (?,?,?,?,?)");

                stmNewTransaction.setString(1, transactionDTO.getAccount());
                stmNewTransaction.setString(2, "CREDIT");
                stmNewTransaction.setString(3, "Deposit");
                stmNewTransaction.setBigDecimal(4, transactionDTO.getAmount());
                stmNewTransaction.setTimestamp(5, new Timestamp(new Date().getTime()));
                if (stmNewTransaction.executeUpdate() != 1) throw new SQLException("Fail to add a new transaction record");

                connection.commit();
                response.setStatus(HttpServletResponse.SC_CREATED);

                ResultSet resultSet = stm.executeQuery();
                resultSet.next();
                String name = resultSet.getString("holder_name");
                String address = resultSet.getString("holder_address");
                BigDecimal balance = resultSet.getBigDecimal("balance");
                AccountDTO accountDTO = new AccountDTO(transactionDTO.getAccount(), name, address, balance);

                response.setStatus(HttpServletResponse.SC_CREATED);
                response.setContentType("application/json");
                JsonbBuilder.create().toJson(accountDTO, response.getWriter());

            }catch (Throwable t){
                connection.rollback();
                t.printStackTrace();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Fail to add a transaction");
            } finally {
                connection.setAutoCommit(true);
            }

            connection.close();

        } catch (JsonException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Fail to withdraw the money to account");
        }
    }
    private void withdrawMoney(TransactionDTO withdrawDTO, HttpServletResponse response) throws IOException {
        try {

            if (withdrawDTO.getAccount() == null || !withdrawDTO.getAccount().matches("[A-Za-z0-9 -]+")){
                throw new JsonException("Invalid account number!");
            } else if (withdrawDTO.getAmount() == null || withdrawDTO.getAmount().compareTo(new BigDecimal(100)) < 0) {
                throw new JsonException("Invalid amount");
            }

            Connection connection = pool.getConnection();
            PreparedStatement stm = connection.prepareStatement("SELECT * FROM Account WHERE account_number=?");
            stm.setString(1, withdrawDTO.getAccount());
            ResultSet rst = stm.executeQuery();

            if (!rst.next()){
                throw new JsonException("Invalid account number");
            }

            try{
                connection.setAutoCommit(false);
                PreparedStatement stmWithdraw = connection.prepareStatement
                        ("UPDATE Account SET balance = balance - ? WHERE account_number=?");
                stmWithdraw.setBigDecimal(1, withdrawDTO.getAmount());
                stmWithdraw.setString(2, withdrawDTO.getAccount());

                ResultSet rst2 = stm.executeQuery();
                rst2.next();
                BigDecimal balance = rst2.getBigDecimal("balance");

                if ((balance.subtract(withdrawDTO.getAmount())).compareTo(new BigDecimal(100)) < 0) throw new JsonException("Insufficient account balance");

                stmWithdraw.executeUpdate();

                PreparedStatement stmNewTransaction = connection.prepareStatement
                        ("INSERT INTO Transaction (account, type, description, amount, date) VALUES (?,?,?,?,?)");

                stmNewTransaction.setString(1, withdrawDTO.getAccount());
                stmNewTransaction.setString(2, "DEBIT");
                stmNewTransaction.setString(3, "Debit");
                stmNewTransaction.setBigDecimal(4, withdrawDTO.getAmount());
                stmNewTransaction.setTimestamp(5, new Timestamp(new Date().getTime()));
                if (stmNewTransaction.executeUpdate() != 1) throw new JsonException("Error while loading the data");

                connection.commit();
                response.setStatus(HttpServletResponse.SC_CREATED);

                ResultSet resultSet = stm.executeQuery();
                resultSet.next();
                String name = resultSet.getString("holder_name");
                String address = resultSet.getString("holder_address");
                BigDecimal balance2 = resultSet.getBigDecimal("balance");
                AccountDTO accountDTO = new AccountDTO(withdrawDTO.getAccount(), name, address, balance2);

                response.setStatus(HttpServletResponse.SC_CREATED);
                response.setContentType("application/json");
                JsonbBuilder.create().toJson(accountDTO, response.getWriter());

            }catch (Throwable t){
                t.printStackTrace();
                connection.rollback();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Fail to withdraw transaction");
            } finally {
                connection.setAutoCommit(true);
            }
            connection.close();

        } catch (SQLException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error while getting the data");
        }
    }
    private void transferMoney(TransferDTO transferDTO, HttpServletResponse resp) throws IOException {
        try {
            // Data validation
            if (transferDTO.getFrom() == null ||
                    !transferDTO.getFrom().matches("[A-Fa-f0-9]{8}(-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}")) {
                throw new JsonException("Invalid from account number");
            } else if (transferDTO.getTo() == null ||
                    !transferDTO.getTo().matches("[A-Fa-f0-9]{8}(-[A-Fa-f0-9]{4}){3}-[A-Fa-f0-9]{12}")) {
                throw new JsonException("Invalid to account number");
            } else if (transferDTO.getAmount() == null ||
                    transferDTO.getAmount().compareTo(new BigDecimal(100)) < 0) {
                throw new JsonException("Invalid amount");
            }

            Connection connection = pool.getConnection();

            PreparedStatement stm1 = connection.prepareStatement("SELECT * FROM Account WHERE account_number = ?");
            stm1.setString(1, transferDTO.getFrom());
            ResultSet rst1;
            synchronized (this){
                rst1 = stm1.executeQuery();
            }
            if(!rst1.next()) throw new JsonException("Invalid account number!");

            PreparedStatement stm2 = connection.prepareStatement("SELECT * FROM Account WHERE account_number = ?");
            stm2.setString(1, transferDTO.getTo());
            ResultSet rst2 = stm2.executeQuery();
            if(!rst2.next()) throw new JsonException("Invalid account number!");

            BigDecimal fromAccBalance = rst1.getBigDecimal("balance");
            BigDecimal toAccBalance = rst2.getBigDecimal("balance");

            if (fromAccBalance.subtract(transferDTO.getAmount()).compareTo(new BigDecimal(100)) < 0){
                throw new JsonException("Insufficient account balance");
            }

            try{
                connection.setAutoCommit(false);

                PreparedStatement withdrawStm = connection.prepareStatement
                        ("UPDATE Account SET balance = ? WHERE account_number = ?");
                withdrawStm.setBigDecimal(1, fromAccBalance.subtract(transferDTO.getAmount()));
                withdrawStm.setString(2, transferDTO.getFrom());
                if (withdrawStm.executeUpdate() != 1) throw new JsonException("Error occurred while depositing the money");

                PreparedStatement withdrawLog = connection.prepareStatement
                        ("INSERT INTO Transaction (account, type, description, amount, date) VALUES (?,?,?,?,?)");
                withdrawLog.setString(1, transferDTO.getFrom());
                withdrawLog.setString(2, "DEBIT");
                withdrawLog.setString(3, "Debit");
                withdrawLog.setBigDecimal(4, transferDTO.getAmount());
                withdrawLog.setTimestamp(5, new Timestamp(new Date().getTime()));
                if (withdrawLog.executeUpdate() != 1) throw new JsonException("Error while recording the transaction");

                PreparedStatement depositStm = connection.prepareStatement
                        ("UPDATE Account SET balance = ? WHERE account_number = ?");
                depositStm.setBigDecimal(1, toAccBalance.add(transferDTO.getAmount()));
                depositStm.setString(2, transferDTO.getTo());
                if (depositStm.executeUpdate() != 1) throw new JsonException("Error occurred while depositing the money");

                PreparedStatement depositLog = connection.prepareStatement
                        ("INSERT INTO Transaction (account, type, description, amount, date) VALUES (?,?,?,?,?)");
                depositLog.setString(1, transferDTO.getTo());
                depositLog.setString(2, "CREDIT");
                depositLog.setString(3, "Credit");
                depositLog.setBigDecimal(4, transferDTO.getAmount());
                depositLog.setTimestamp(5, new Timestamp(new Date().getTime()));
                if (depositLog.executeUpdate() != 1) throw new JsonException("Fail to add credit transaction records");

                connection.commit();

                ResultSet resultSet = stm1.executeQuery();
                resultSet.next();
                String name = resultSet.getString("holder_name");
                String address = resultSet.getString("holder_address");
                BigDecimal balance = resultSet.getBigDecimal("balance");
                AccountDTO accountDTO = new AccountDTO(transferDTO.getFrom(), name, address, balance);

                resp.setStatus(HttpServletResponse.SC_CREATED);
                resp.setContentType("application/json");
                JsonbBuilder.create().toJson(accountDTO, resp.getWriter());

            } catch (Throwable t){
                connection.rollback();
                t.printStackTrace();
                resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Fail to transfer the money, please contact the bank");
            } finally {
                connection.setAutoCommit(true);
            }
            connection.close();

        } catch (SQLException e) {
            e.printStackTrace();
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error occurred while getting the connection to the DB");
        }
    }
}
