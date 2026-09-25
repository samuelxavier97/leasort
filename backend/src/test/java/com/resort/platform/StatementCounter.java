package com.resort.platform;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Conta os statements JDBC preparados em toda a suíte, por qualquer caminho (Hibernate, JdbcClient, Spring
 * Session). Serve para provar que o número de consultas de uma requisição não cresce com o volume de dados.
 */
public class StatementCounter extends DelegatingDataSource {

    private static final AtomicLong COUNT = new AtomicLong();
    private static final Set<String> COUNTED = Set.of("prepareStatement", "prepareCall", "createStatement");

    public StatementCounter(DataSource target) {
        super(target);
    }

    public static long count() {
        return COUNT.get();
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
                    if (COUNTED.contains(method.getName())) {
                        COUNT.incrementAndGet();
                    }
                    try {
                        return method.invoke(connection, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
