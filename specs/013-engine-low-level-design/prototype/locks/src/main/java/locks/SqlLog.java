package locks;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/** Records every SQL statement Hibernate prepares, so tests can assert on the lock clause. */
public class SqlLog implements StatementInspector {
    public static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        STATEMENTS.add(sql);
        return sql;
    }

    public static List<String> matching(String fragment) {
        return STATEMENTS.stream().filter(s -> s.toLowerCase().contains(fragment)).toList();
    }
}
