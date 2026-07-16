package com.queuectl.repository;

import com.queuectl.domain.WorkerInfo;
import com.queuectl.exception.PersistenceException;
import com.queuectl.persistence.SqliteConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite implementation of {@link WorkerRepository}.
 */
public class SqliteWorkerRepository implements WorkerRepository {

    private static final Logger logger = LoggerFactory.getLogger(SqliteWorkerRepository.class);

    private final SqliteConnectionFactory connectionFactory;

    public SqliteWorkerRepository(SqliteConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void save(WorkerInfo worker) {
        String sql = "INSERT OR REPLACE INTO workers (id, status, heartbeat, started_at) VALUES (?, ?, ?, ?)";

        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, worker.getId());
            ps.setString(2, worker.getStatus());
            ps.setString(3, worker.getHeartbeat());
            ps.setString(4, worker.getStartedAt());
            ps.executeUpdate();
            logger.debug("Worker saved: {}", worker.getId());

        } catch (SQLException e) {
            throw new PersistenceException("Failed to save worker: " + worker.getId(), e);
        }
    }

    @Override
    public Optional<WorkerInfo> findById(String id) {
        String sql = "SELECT * FROM workers WHERE id = ?";

        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new PersistenceException("Failed to find worker: " + id, e);
        }
    }

    @Override
    public List<WorkerInfo> findAll() {
        String sql = "SELECT * FROM workers ORDER BY started_at DESC";
        List<WorkerInfo> workers = new ArrayList<>();

        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                workers.add(mapResultSet(rs));
            }
            return workers;

        } catch (SQLException e) {
            throw new PersistenceException("Failed to find all workers", e);
        }
    }

    @Override
    public void updateHeartbeat(String id, String heartbeat) {
        String sql = "UPDATE workers SET heartbeat = ? WHERE id = ?";

        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, heartbeat);
            ps.setString(2, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new PersistenceException("Failed to update heartbeat for worker: " + id, e);
        }
    }

    @Override
    public void updateStatus(String id, String status) {
        String sql = "UPDATE workers SET status = ? WHERE id = ?";

        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setString(2, id);
            ps.executeUpdate();
            logger.debug("Worker {} status updated to {}", id, status);

        } catch (SQLException e) {
            throw new PersistenceException("Failed to update status for worker: " + id, e);
        }
    }

    @Override
    public boolean delete(String id) {
        String sql = "DELETE FROM workers WHERE id = ?";

        try (Connection conn = connectionFactory.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, id);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new PersistenceException("Failed to delete worker: " + id, e);
        }
    }

    @Override
    public int deleteAll() {
        String sql = "DELETE FROM workers";

        try (Connection conn = connectionFactory.getConnection();
             Statement stmt = conn.createStatement()) {

            int count = stmt.executeUpdate(sql);
            logger.info("Deleted all {} worker registrations", count);
            return count;

        } catch (SQLException e) {
            throw new PersistenceException("Failed to delete all workers", e);
        }
    }

    @Override
    public int countActive() {
        String sql = "SELECT COUNT(*) FROM workers WHERE status = 'RUNNING'";

        try (Connection conn = connectionFactory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            return rs.next() ? rs.getInt(1) : 0;

        } catch (SQLException e) {
            throw new PersistenceException("Failed to count active workers", e);
        }
    }

    private WorkerInfo mapResultSet(ResultSet rs) throws SQLException {
        WorkerInfo worker = new WorkerInfo();
        worker.setId(rs.getString("id"));
        worker.setStatus(rs.getString("status"));
        worker.setHeartbeat(rs.getString("heartbeat"));
        worker.setStartedAt(rs.getString("started_at"));
        return worker;
    }
}
