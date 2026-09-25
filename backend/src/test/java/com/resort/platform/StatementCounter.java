package com.resort.platform;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Conta os statements JDBC preparados em toda a suíte, por qualquer caminho (Hibernate, JdbcClient, Spring
 * Session), e registra os {@code fetchSize} pedidos. Serve para provar que o número de consultas de uma
 * requisição não cresce com o volume de dados e que a exportação lê com cursor (D-098, D-101).
 */
public class StatementCounter extends DelegatingDataSource {

    private static final AtomicLong COUNT = new AtomicLong();
    private static final List<Integer> FETCH_SIZES = new CopyOnWriteArrayList<>();
    private static final Set<String> COUNTED = Set.of("prepareStatement", "prepareCall", "createStatement");

    public StatementCounter(DataSource target) {
        super(target);
    }

    public static long count() {
        return COUNT.get();
    }

    /** {@code fetchSize} pedidos desde o início da suíte, na ordem. */
    public static List<Integer> fetchSizes() {
        return List.copyOf(FETCH_SIZES);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return counting(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return counting(super.getConnection(username, password));
    }

    private static Connection counting(Connection connection) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    Object result = invoke(connection, method, args);
                    if (COUNTED.contains(method.getName())) {
                        COUNT.incrementAndGet();
                        return recordingFetchSize((Statement) result);
                    }
                    return result;
                });
    }

    private static Object recordingFetchSize(Statement statement) {
        Class<?>[] interfaces = statement.getClass().getInterfaces().length == 0
                ? new Class<?>[] {Statement.class}
                : allJdbcInterfaces(statement);
        return Proxy.newProxyInstance(Statement.class.getClassLoader(), interfaces, (proxy, method, args) -> {
            if (method.getName().equals("setFetchSize")) {
                FETCH_SIZES.add((Integer) args[0]);
            }
            return invoke(statement, method, args);
        });
    }

    private static Class<?>[] allJdbcInterfaces(Statement statement) {
        if (statement instanceof java.sql.CallableStatement) {
            return new Class<?>[] {java.sql.CallableStatement.class};
        }
        if (statement instanceof java.sql.PreparedStatement) {
            return new Class<?>[] {java.sql.PreparedStatement.class};
        }
        return new Class<?>[] {Statement.class};
    }

    private static Object invoke(Object target, java.lang.reflect.Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
