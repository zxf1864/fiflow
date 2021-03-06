package com.github.lessonone.fiflow.io.elasticsearch7.source;

import com.github.lessonone.fiflow.core.io.PushExpressionUtils;
import com.github.lessonone.fiflow.core.io.TypeUtils;
import com.github.lessonone.fiflow.io.elasticsearch7.core.ESOptions;
import com.github.lessonone.fiflow.io.elasticsearch7.core.ESTableBaseBuilder;
import org.apache.flink.api.java.typeutils.RowTypeInfo;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.TableSchema;
import org.apache.flink.table.expressions.Expression;
import org.apache.flink.table.functions.AsyncTableFunction;
import org.apache.flink.table.functions.TableFunction;
import org.apache.flink.table.sources.FilterableTableSource;
import org.apache.flink.table.sources.LookupableTableSource;
import org.apache.flink.table.sources.StreamTableSource;
import org.apache.flink.table.sources.TableSource;
import org.apache.flink.types.Row;

import java.util.List;

import static org.apache.flink.util.Preconditions.checkNotNull;

public class ESTableSource implements StreamTableSource<Row>, FilterableTableSource<Row>, LookupableTableSource<Row> {

    private final ESOptions esOptions;
    private final TableSchema schema;
    private final RowTypeInfo typeInfo;
    // 过滤条件部分
    private final String where;


    private ESTableSource(ESOptions esOptions, TableSchema schema, String where) {
        this.esOptions = esOptions;
        this.schema = schema;
        this.typeInfo = TypeUtils.toNormalizeRowType(schema);
        this.where = where;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean isBounded() {
        return true;
    }

    @Override
    public TableFunction<Row> getLookupFunction(String[] lookupKeys) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AsyncTableFunction<Row> getAsyncLookupFunction(String[] lookupKeys) {
        return new ESAsyncLookupFun(esOptions, typeInfo, lookupKeys);
    }

    @Override
    public boolean isAsyncEnabled() {
        return true;
    }

    @Override
    public DataStream<Row> getDataStream(StreamExecutionEnvironment execEnv) {
        return execEnv.addSource(ESSourceFunction.builder()
                .setEsOptions(esOptions)
                .setRowTypeInfo(typeInfo)
                .setWhere(where)
                .build()).name(explainSource());
    }

    @Override
    public RowTypeInfo getReturnType() {
        return typeInfo;
    }

    @Override
    public TableSchema getTableSchema() {
        return schema;
    }

    @Override
    public String explainSource() {
        StringBuilder sb = new StringBuilder();
        String className = this.getClass().getSimpleName();
        String[] fields = typeInfo.getFieldNames();
        if (null == fields) {
            sb.append(className + "(*)");
        } else {
            sb.append(className + "(" + String.join(", ", fields) + ")");
        }
        sb.append(" from ").append(esOptions.getIndex());
        if (where != null) {
            sb.append(" where ").append(where);
        }
        return sb.toString();
    }

    @Override
    public TableSource<Row> applyPredicate(List<Expression> predicates) {
        String where = PushExpressionUtils.toWhere(predicates, null);
        return new ESTableSource(esOptions, schema, where);
    }

    @Override
    public boolean isFilterPushedDown() {
        return where != null;
    }

    public static class Builder extends ESTableBaseBuilder {
        private String where;

        public Builder setWhere(String where) {
            this.where = where;
            return this;
        }

        /**
         * Finalizes the configuration and checks validity.
         *
         * @return Configured JDBCTableSource
         */
        public ESTableSource build() {
            checkNotNull(esOptions, "No EsOptions supplied.");
            checkNotNull(schema, "No schema supplied.");
            return new ESTableSource(esOptions, schema, where);
        }
    }
}