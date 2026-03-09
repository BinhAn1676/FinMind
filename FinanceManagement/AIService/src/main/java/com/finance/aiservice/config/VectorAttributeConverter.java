package com.finance.aiservice.config;

import com.pgvector.PGvector;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.postgresql.util.PGobject;

import java.sql.SQLException;

/**
 * Hibernate AttributeConverter for pgvector type.
 *
 * Converts between Java float[] and PostgreSQL vector type.
 * Required because Hibernate doesn't natively support pgvector.
 */
@Converter(autoApply = false)
public class VectorAttributeConverter implements AttributeConverter<float[], PGobject> {

    /**
     * Convert float[] to PGobject for database storage.
     *
     * PostgreSQL vector type requires PGobject with type="vector"
     */
    @Override
    public PGobject convertToDatabaseColumn(float[] attribute) {
        if (attribute == null) {
            return null;
        }

        try {
            PGvector vector = new PGvector(attribute);
            PGobject pgObject = new PGobject();
            pgObject.setType("vector");
            pgObject.setValue(vector.toString());  // Format: "[0.1, 0.2, 0.3]"
            return pgObject;
        } catch (SQLException e) {
            throw new IllegalArgumentException("Failed to convert float[] to PGvector", e);
        }
    }

    /**
     * Convert PGobject from database to float[].
     *
     * Input: PGobject with value "[0.1, 0.2, 0.3]"
     */
    @Override
    public float[] convertToEntityAttribute(PGobject dbData) {
        if (dbData == null || dbData.getValue() == null) {
            return null;
        }

        try {
            PGvector vector = new PGvector(dbData.getValue());
            return vector.toArray();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert database vector to float[]: " + dbData.getValue(), e);
        }
    }
}

