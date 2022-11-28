package org.broadinstitute.dsm.db.dao.dashboard;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.broadinstitute.dsm.db.dto.dashboard.DashboardDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelDto;
import org.broadinstitute.dsm.db.dto.dashboard.DashboardLabelFilterDto;
import org.broadinstitute.dsm.model.dashboard.DisplayType;
import org.broadinstitute.dsm.model.dashboard.Size;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DashboardDaoImpl implements DashboardDao {

    private static final Logger logger = LoggerFactory.getLogger(DashboardDaoImpl.class);

    private static final String SQL_DASHBOARD_DATA_BY_ID = "SELECT d.dashboard_id, d.ddp_instance_id, d.display_text, d.size, "
            + "d.ordering, "
            + "d.display_type, dl.label_id, dl.label_name, dl.color, df.label_filter_id, df.es_filter_path, df.es_filter_path_value, "
            + "df.es_nested_path, df.additional_filter "
            + "FROM dashboard as d "
            + "LEFT JOIN dashboard_label as dl ON d.dashboard_id = dl.dashboard_id "
            + "LEFT JOIN dashboard_label_filter as df ON dl.label_id = df.label_id "
            + "WHERE d.dashboard_id = ?";

    private static final String SQL_DASHBOARD_DATAS_BY_INSTANCE_ID = "SELECT d.dashboard_id, d.ddp_instance_id, d.display_text, d.size, "
            + "d.ordering, "
            + "d.display_type, dl.label_id, dl.label_name, dl.color, df.label_filter_id, df.es_filter_path, df.es_filter_path_value, "
            + "df.es_nested_path, df.additional_filter "
            + "FROM dashboard as d "
            + "LEFT JOIN dashboard_label as dl ON d.dashboard_id = dl.dashboard_id "
            + "LEFT JOIN dashboard_label_filter as df ON dl.label_id = df.label_id "
            + "WHERE d.ddp_instance_id = ? ";

    private static final String SQL_DISPLAY_CHARTS = "AND display_type != 'COUNT'";
    private static final String SQL_DISPLAY_COUNTS = "AND display_type = 'COUNT'";

    private static final String SQL_DELETE_DASHBOARD_BY_ID = "DELETE FROM dashboard WHERE dashboard_id = ?";
    private static final String SQL_DELETE_DASHBOARD_LABEL_BY_ID = "DELETE FROM dashboard_label WHERE label_id = ?";
    private static final String SQL_DELETE_DASHBOARD_LABEL_FILTER_BY_ID = "DELETE FROM dashboard_label_filter WHERE label_filter_id = ?";

    private static final String SQL_INSERT_DASHBOARD = "INSERT INTO dashboard"
            + "(ddp_instance_id, display_text, size, ordering, display_type) "
            + "VALUES (?, ?, ?, ?, ?)";

    private static final String SQL_INSERT_LABEL = "INSERT INTO dashboard_label"
            + "(label_name, color, dashboard_id) "
            + "VALUES (?, ?, ?)";

    private static final String SQL_INSERT_LABEL_FILTER = "INSERT INTO dashboard_label_filter"
            + "(es_filter_path, es_filter_path_value, es_nested_path, additional_filter, label_id) "
            + "VALUES (?, ?, ?, ?, ?)";
    public static final String DASHBOARD_ID = "dashboard_id";
    public static final String DDP_INSTANCE_ID = "ddp_instance_id";
    public static final String DISPLAY_TEXT = "display_text";
    public static final String DISPLAY_TYPE = "display_type";
    public static final String ORDERING = "ordering";
    public static final String SIZE = "size";
    public static final String LABEL_ID = "label_id";
    public static final String LABEL_NAME = "label_name";
    public static final String COLOR = "color";
    public static final String LABEL_FILTER_ID = "label_filter_id";
    public static final String ES_FILTER_PATH = "es_filter_path";
    public static final String ES_FILTER_PATH_VALUE = "es_filter_path_value";
    public static final String ES_NESTED_PATH = "es_nested_path";
    public static final String ADDITIONAL_FILTER = "additional_filter";

    @Override
    public int create(DashboardDto dashboardDto) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_DASHBOARD, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setInt(1, dashboardDto.getDdpInstanceId());
                stmt.setString(2, dashboardDto.getDisplayText());
                stmt.setString(3, dashboardDto.getSize().toString());
                stmt.setInt(4, dashboardDto.getOrder());
                stmt.setString(5, dashboardDto.getDisplayType().toString());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException sqle) {
                dbVals.resultException = sqle;
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new RuntimeException(
                    String.format("Error inserting dashboard for ddp instance with id: %s", dashboardDto.getDdpInstanceId()),
                    simpleResult.resultException);
        }
        logger.info(
                String.format(
                        "Dashboard: %s has been created successfully for instance with id: %s",
                        dashboardDto.getDisplayText(), dashboardDto.getDdpInstanceId()
                ));
        return (int) simpleResult.resultValue;
    }

    @Override
    public int delete(int id) {
        return deleteById(id, SQL_DELETE_DASHBOARD_BY_ID, "Error deleting dashboard with id: " + id);
    }

    @Override
    public Optional<DashboardDto> get(long id) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DASHBOARD_DATA_BY_ID)) {
                stmt.setLong(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    Optional<DashboardDto> maybeDashboardDto = Optional.empty();
                    if (rs.next()) {
                        DashboardLabelFilterDto dashboardFilterDto = buildLabelFilterDtoFrom(rs);
                        DashboardLabelDto dashboardLabelDto = buildLabelDtoFrom(rs, dashboardFilterDto);
                        List<DashboardLabelDto> dashboardLabelDtos =
                                List.of(dashboardLabelDto);
                        maybeDashboardDto = Optional.of(buildDashboardDtoFrom(rs, dashboardLabelDtos));
                    }
                    while (rs.next()) {
                        DashboardLabelFilterDto dashboardFilterDto = buildLabelFilterDtoFrom(rs);
                        maybeDashboardDto.get().getLabels().add(buildLabelDtoFrom(rs, dashboardFilterDto));
                    }
                    execResult.resultValue = maybeDashboardDto;
                }
            } catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting dashboard data with " + id, results.resultException);
        }
        return (Optional<DashboardDto>) results.resultValue;
    }

    private DashboardDto buildDashboardDtoFrom(ResultSet rs, List<DashboardLabelDto> dashboardLabelDtos) throws SQLException {
        return new DashboardDto.Builder()
                .withDashboardId(rs.getInt(DASHBOARD_ID))
                .withDdpInstanceId(rs.getInt(DDP_INSTANCE_ID))
                .withDisplayText(rs.getString(DISPLAY_TEXT))
                .withDisplayType(DisplayType.valueOf(rs.getString(DISPLAY_TYPE)))
                .withOrder(rs.getInt(ORDERING))
                .withSize(Size.valueOf(rs.getString(SIZE)))
                .withLabels(new ArrayList<>(dashboardLabelDtos))
                .build();
    }

    private DashboardLabelDto buildLabelDtoFrom(ResultSet rs, DashboardLabelFilterDto dashboardFilterDto) throws SQLException {
        return new DashboardLabelDto.Builder()
                .withLabelId(rs.getInt(LABEL_ID))
                .withLabelName(rs.getString(LABEL_NAME))
                .withColor(rs.getString(COLOR))
                .withDashboardId(rs.getInt(DASHBOARD_ID))
                .withDashboardLabelFilter(dashboardFilterDto)
                .build();
    }

    private DashboardLabelFilterDto buildLabelFilterDtoFrom(ResultSet rs) throws SQLException {
        return new DashboardLabelFilterDto.Builder()
                .withLabelFilterId(rs.getInt(LABEL_FILTER_ID))
                .withEsFilterPath(rs.getString(ES_FILTER_PATH))
                .withEsFilterPathValue(rs.getString(ES_FILTER_PATH_VALUE))
                .withEsNestedPath(rs.getString(ES_NESTED_PATH))
                .withAdditionalFilter(rs.getString(ADDITIONAL_FILTER))
                .withLabelId(rs.getInt(LABEL_ID))
                .build();
    }

    @Override
    public List<DashboardDto> getByInstanceId(int instanceId, boolean charts) {
        List<DashboardDto> result = new ArrayList<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DASHBOARD_DATAS_BY_INSTANCE_ID
                    + (charts ? SQL_DISPLAY_CHARTS : SQL_DISPLAY_COUNTS))) {
                stmt.setLong(1, instanceId);
                try (ResultSet rs = stmt.executeQuery()) {
                    Map<Integer, DashboardDto> map = new HashMap<>();
                    while (rs.next()) {
                        int dashboardId = rs.getInt(DASHBOARD_ID);
                        DashboardLabelFilterDto dashboardFilterDto = buildLabelFilterDtoFrom(rs);
                        DashboardLabelDto dashboardLabelDto = buildLabelDtoFrom(rs, dashboardFilterDto);
                        DashboardDto dashboardDto = buildDashboardDtoFrom(rs, new ArrayList<>(List.of(dashboardLabelDto)));
                        map.merge(dashboardId, dashboardDto, (prev, curr) -> {
                            prev.getLabels().add(dashboardLabelDto);
                            return prev;
                        });
                    }
                    result.addAll(map.values());
                }
            } catch (SQLException ex) {
                execResult.resultException = ex;
            }
            return execResult;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error getting dashboard datas with " + instanceId, results.resultException);
        }
        logger.info(
                String.format(
                        "Got %s dashboard graph for instance with id: %s ",
                        result.size(), instanceId
                ));
        return result;
    }

    @Override
    public int createLabel(DashboardLabelDto dashboardLabelDto) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_LABEL, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, dashboardLabelDto.getLabelName());
                stmt.setString(2, dashboardLabelDto.getColor());
                stmt.setInt(3, dashboardLabelDto.getDashboardId());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException sqle) {
                dbVals.resultException = sqle;
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new RuntimeException(
                    String.format("Error inserting dashboard label for dashboard with id: %s", dashboardLabelDto.getDashboardId()),
                    simpleResult.resultException);
        }
        logger.info(
                String.format(
                        "Dashboard label: %s has been created successfully for instance with id: %s",
                        dashboardLabelDto.getLabelName(), dashboardLabelDto.getDashboardId()
                ));
        return (int) simpleResult.resultValue;
    }

    @Override
    public int createFilter(DashboardLabelFilterDto dashboardLabelFilterDto) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_LABEL_FILTER, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, dashboardLabelFilterDto.getEsFilterPath());
                stmt.setString(2, dashboardLabelFilterDto.getEsFilterPathValue());
                stmt.setString(3, dashboardLabelFilterDto.getEsNestedPath());
                stmt.setString(4, dashboardLabelFilterDto.getAdditionalFilter());
                stmt.setInt(5, dashboardLabelFilterDto.getLabelId());
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getInt(1);
                    }
                }
            } catch (SQLException sqle) {
                dbVals.resultException = sqle;
            }
            return dbVals;
        });
        if (simpleResult.resultException != null) {
            throw new RuntimeException(
                    String.format("Error inserting dashboard label filter for dashboard label with id: %s",
                            dashboardLabelFilterDto.getLabelId()), simpleResult.resultException);
        }
        logger.info(
                String.format(
                        "Dashboard label filter has been created successfully for dashboard label with id: %s",
                        dashboardLabelFilterDto.getLabelId()
                ));
        return (int) simpleResult.resultValue;
    }

    @Override
    public int deleteLabel(int labelId) {
        return deleteById(labelId, SQL_DELETE_DASHBOARD_LABEL_BY_ID, "Error deleting dashboard label with id: " + labelId);
    }

    @Override
    public int deleteFilter(int filterId) {
        return deleteById(filterId, SQL_DELETE_DASHBOARD_LABEL_FILTER_BY_ID, "Error deleting dashboard label filter with id: "
                + filterId);
    }

    private int deleteById(int id, String sql, String errorMessage) {
        SimpleResult simpleResult = inTransaction(conn -> {
            SimpleResult execResult = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, id);
                execResult.resultValue = stmt.executeUpdate();
            } catch (SQLException sqle) {
                execResult.resultException = sqle;
            }
            return execResult;
        });

        if (simpleResult.resultException != null) {
            throw new RuntimeException(errorMessage, simpleResult.resultException);
        }
        return (int) simpleResult.resultValue;
    }
}
