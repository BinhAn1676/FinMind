package com.finance.aiservice.config;

import com.pgvector.PGvector;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.*;

/**
 * Hibernate UserType for pgvector.
 *
 * Provides complete control over how float[] is serialized/deserialized
 * to/from PostgreSQL vector type.
 */
public class VectorType implements UserType<float[]> {

    @Override
    public int getSqlType() {
        return Types.OTHER;  // pgvector is a custom PostgreSQL type
    }

    @Override
    public Class<float[]> returnedClass() {
        return float[].class;
    }

    @Override
    public boolean equals(float[] x, float[] y) {
        if (x == y) return true;
        if (x == null || y == null) return false;
        if (x.length != y.length) return false;

        for (int i = 0; i < x.length; i++) {
            if (Float.compare(x[i], y[i]) != 0) return false;
        }
        return true;
    }

    @Override
    public int hashCode(float[] x) {
        if (x == null) return 0;
        int result = 1;
        for (float v : x) {
            result = 31 * result + Float.hashCode(v);
        }
        return result;
    }

    @Override
    public float[] nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
            throws SQLException {

        Object value = rs.getObject(position);

        if (value == null) {
            return null;
        }

        try {
            // PostgreSQL JDBC returns PGobject for vector type
            if (value instanceof org.postgresql.util.PGobject) {
                org.postgresql.util.PGobject pgObj = (org.postgresql.util.PGobject) value;
                PGvector vector = new PGvector(pgObj.getValue());
                return vector.toArray();
            }

            // Fallback: if it's already a PGvector
            if (value instanceof PGvector) {
                return ((PGvector) value).toArray();
            }

            // Fallback: if it's a String representation
            if (value instanceof String) {
                PGvector vector = new PGvector((String) value);
                return vector.toArray();
            }

            throw new IllegalArgumentException("Unexpected type for vector column: " + value.getClass());

        } catch (Exception e) {
            throw new SQLException("Failed to read vector from database", e);
        }
    }

    @Override
    public void nullSafeSet(PreparedStatement st, float[] value, int index, SharedSessionContractImplementor session)
            throws SQLException {

        if (value == null) {
            st.setNull(index, Types.OTHER);
            return;
        }

        try {
            PGvector vector = new PGvector(value);
            st.setObject(index, vector);
        } catch (Exception e) {
            throw new SQLException("Failed to write vector to database", e);
        }
    }

    @Override
    public float[] deepCopy(float[] value) {
        if (value == null) return null;
        return value.clone();
    }

    @Override
    public boolean isMutable() {
        return true;  // float[] is mutable
    }

    @Override
    public Serializable disassemble(float[] value) {
        return deepCopy(value);
    }

    @Override
    public float[] assemble(Serializable cached, Object owner) {
        return deepCopy((float[]) cached);
    }
}
