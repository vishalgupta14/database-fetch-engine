package com.platform.engine.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "test_data_all_types", schema = "public")
public class TestDataAllTypes {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "int_col")
    private Integer intCol;

    @Column(name = "bigint_col")
    private Long bigintCol;

    @Column(name = "decimal_col", precision = 10, scale = 2)
    private BigDecimal decimalCol;

    @Column(name = "double_col")
    private Double doubleCol;

    @Column(name = "bool_col")
    private Boolean boolCol;

    @Column(name = "date_col")
    private LocalDate dateCol;

    @Column(name = "time_col")
    private LocalTime timeCol;

    @Column(name = "timestamp_col")
    private LocalDateTime timestampCol;

    @Column(name = "char_col", length = 10)
    private String charCol;

    @Column(name = "varchar_col", length = 255)
    private String varcharCol;

    @Column(name = "text_col")
    private String textCol;

    @Column(name = "uuid_col")
    private UUID uuidCol;

    @Column(name = "json_col", columnDefinition = "jsonb")
    private String jsonCol;

    @Column(name = "string_int")
    private String stringInt;

    @Column(name = "string_bigint")
    private String stringBigint;

    @Column(name = "string_decimal")
    private String stringDecimal;

    @Column(name = "string_double")
    private String stringDouble;

    @Column(name = "string_boolean")
    private String stringBoolean;

    @Column(name = "string_date")
    private String stringDate;

    @Column(name = "string_time")
    private String stringTime;

    @Column(name = "string_timestamp")
    private String stringTimestamp;

    @Column(name = "string_char")
    private String stringChar;

    @Column(name = "string_varchar")
    private String stringVarchar;

    @Column(name = "string_uuid")
    private String stringUuid;

    @Column(name = "string_json")
    private String stringJson;
}
