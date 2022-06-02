package org.gauss.util.ddl.convert;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.gauss.converter.ColumnTypeConverter;
import org.gauss.jsonstruct.DDLValueStruct;
import org.gauss.jsonstruct.SourceStruct;
import org.gauss.jsonstruct.TableChangeStruct;
import org.gauss.util.OpenGaussConstant;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author saxisuer
 * @Description
 * @date 2022/4/20
 * @email sheng.pu@enmotech.com
 * @COMPANY ENMOTECH
 */
public class CreateTableConvert extends BaseConvert implements DDLConvert {

    public String parse(DDLValueStruct struct) {
        List<TableChangeStruct> tableChanges = struct.getPayload().getTableChanges();
        List<String> openGaussSqlList = tableChanges.stream()
                                                    .map(tableChangeStruct -> convertSqlToOpenGaussSql(tableChangeStruct,
                                                                                                       struct.getPayload().getSource()))
                                                    .collect(Collectors.toList());
        return StringUtils.join(openGaussSqlList, OpenGaussConstant.SEMICOLON);
    }

    private String convertSqlToOpenGaussSql(TableChangeStruct tableChangeStruct, SourceStruct source) {
        return convertCreateSqlToOpenGaussSql(tableChangeStruct, source);
    }

    private String convertCreateSqlToOpenGaussSql(TableChangeStruct tableChangeStruct, SourceStruct source) {
        List<String> columnSqls = tableChangeStruct.getTable().getColumns().stream().map(column -> getColumnSqls(column))
                                                   .collect(Collectors.toList());

        String primaryKeySql = getPrimaryKeySql(tableChangeStruct.getTable().getPrimaryKeyColumnNames());

        List<String> foreignKeySqls = tableChangeStruct.getTable().getForeignKeyColumns().stream()
                                                       .map(foreignKeyColumn -> getForeignKeySql(foreignKeyColumn)).collect(Collectors.toList());

        List<String> uniqueColumnSqls = tableChangeStruct.getTable().getUniqueColumns().stream()
                                                         .map(uniqueColumn -> getUniqueSql(uniqueColumn)).collect(Collectors.toList());

        List<String> checkColumnSqls = tableChangeStruct.getTable().getCheckColumns().stream()
                                                        .map(checkColumn -> getCheckSql(checkColumn)).collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append(getTableTitleSql(source))
          .append(OpenGaussConstant.BRACKETS_START);

        sb.append(StringUtils.join(columnSqls, getColumnJoinStr()));

        sb.append(StringUtils.isNotEmpty(primaryKeySql)? OpenGaussConstant.COMMA : StringUtils.EMPTY);
        sb.append(primaryKeySql);

        sb.append(CollectionUtils.isNotEmpty(uniqueColumnSqls)? OpenGaussConstant.COMMA : StringUtils.EMPTY);
        sb.append(StringUtils.join(uniqueColumnSqls, getColumnJoinStr()));

        sb.append(CollectionUtils.isNotEmpty(checkColumnSqls)? OpenGaussConstant.COMMA : StringUtils.EMPTY);
        sb.append(StringUtils.join(checkColumnSqls, getColumnJoinStr()));

        sb.append(CollectionUtils.isNotEmpty(foreignKeySqls)? OpenGaussConstant.COMMA : StringUtils.EMPTY);
        sb.append(StringUtils.join(foreignKeySqls, getColumnJoinStr()));

        sb.append(OpenGaussConstant.BRACKETS_ENDT);
        return sb.toString();
    }


    private String  getUniqueSql(TableChangeStruct.IndexColumn uniqueColumn) {
        StringBuilder sb = new StringBuilder();
        sb.append(StringUtils.LF);
        sb.append(OpenGaussConstant.TAB);
        sb.append("CONSTRAINT ");
        sb.append(wrapQuote(uniqueColumn.getIndexName())).append(StringUtils.SPACE);
        sb.append("UNIQUE ");
        sb.append(StringUtils.SPACE)
          .append(addBrackets(wrapQuote(uniqueColumn.getColumnName())));
        return sb.toString();
    }

    private String getCheckSql(TableChangeStruct.CheckColumn checkColumn) {
        StringBuilder sb = new StringBuilder();
        sb.append(StringUtils.LF);
        sb.append(OpenGaussConstant.TAB);
        sb.append("CONSTRAINT ");
        sb.append(wrapQuote(checkColumn.getIndexName())).append(StringUtils.SPACE);
        sb.append("CHECK ");
        sb.append(StringUtils.SPACE)
          .append(addBrackets(checkColumn.getCondition()));
        return sb.toString();
    }

    private String getForeignKeySql(TableChangeStruct.ForeignKeyColumn foreignKeyColumn) {

        String fkColumnNameStr = wrapQuote(foreignKeyColumn.getFkColumnName());
        if (foreignKeyColumn.getFkColumnName().contains(String.valueOf(OpenGaussConstant.COMMA))){
            fkColumnNameStr =
                    StringUtils.join(Arrays.stream(foreignKeyColumn.getFkColumnName().split(String.valueOf(OpenGaussConstant.COMMA)))
                                           .map(this::wrapQuote).collect(Collectors.toList()),
                                     String.valueOf(OpenGaussConstant.COMMA));
        }

        String pkColumnNameStr = wrapQuote(foreignKeyColumn.getPkColumnName());
        if (foreignKeyColumn.getFkColumnName().contains(String.valueOf(OpenGaussConstant.COMMA))){
            pkColumnNameStr =
                    StringUtils.join(Arrays.stream(foreignKeyColumn.getPkColumnName().split(String.valueOf(OpenGaussConstant.COMMA)))
                                           .map(this::wrapQuote).collect(Collectors.toList()),
                                     String.valueOf(OpenGaussConstant.COMMA));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(StringUtils.LF);
        sb.append(OpenGaussConstant.TAB);
        sb.append("CONSTRAINT ");
        sb.append(wrapQuote(foreignKeyColumn.getFkName())).append(StringUtils.SPACE);
        sb.append("FOREIGN KEY ");
        sb.append(StringUtils.SPACE)
          .append(addBrackets(fkColumnNameStr))
          .append(StringUtils.SPACE);
        sb.append(StringUtils.LF);
        sb.append(OpenGaussConstant.TAB);
        sb.append("REFERENCES ");
        sb.append(StringUtils.SPACE)
          .append(wrapQuote(foreignKeyColumn.getPktableSchem()))
          .append(OpenGaussConstant.DOT)
          .append(wrapQuote(foreignKeyColumn.getPktableName()))
          .append(StringUtils.SPACE)
          .append(addBrackets(pkColumnNameStr));
        return sb.toString();
    }

    private String getPrimaryKeySql(List<String> primaryKeys) {
        Set<String> primaryKeySet = primaryKeys.stream().map(primaryKey -> wrapQuote(primaryKey)).collect(Collectors.toSet());
        StringBuilder sb = new StringBuilder();
        if (!primaryKeySet.isEmpty()) {
            sb.append(StringUtils.LF);
            sb.append(OpenGaussConstant.TAB);
            sb.append("PRIMARY KEY ");
            sb.append(addBrackets(StringUtils.join(primaryKeySet, OpenGaussConstant.COMMA)));
            sb.append(StringUtils.LF);
        }
        return sb.toString();
    }

    private String getTableTitleSql(SourceStruct source) {
        StringBuilder sb = new StringBuilder();
        sb.append(StringUtils.LF)
          .append(OpenGaussConstant.TABLE_CREATE)
          .append(StringUtils.SPACE)
          .append("TABLE")
          .append(StringUtils.SPACE)
          .append(wrapQuote(source.getSchema()))
          .append(OpenGaussConstant.DOT)
          .append(wrapQuote(source.getTable()))
          .append(StringUtils.LF);
        return sb.toString();
    }

    private String getColumnSqls(TableChangeStruct.column column) {
        StringBuilder sb = new StringBuilder();
        sb.append(OpenGaussConstant.TAB);
        sb.append(wrapQuote(ColumnTypeConverter.convertTypeName(column.getName()))).append(StringUtils.SPACE);
        sb.append(column.getTypeName())
          .append(column.getLength() > NumberUtils.INTEGER_ZERO? addBrackets(column.getLength()) : StringUtils.EMPTY)
          .append(StringUtils.SPACE);
        if (StringUtils.isNotEmpty(column.getDefaultValueExpression())){
            sb.append("DEFAULT ").append(column.getDefaultValueExpression()).append(StringUtils.SPACE);
        }
        sb.append(column.isOptional()? StringUtils.EMPTY : "NOT NULL");
        return sb.toString();
    }

    @Override
    public String convertToOpenGaussDDL(DDLValueStruct ddlValueStruct) {
        return parse(ddlValueStruct);
    }
}