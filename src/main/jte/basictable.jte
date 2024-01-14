<%--@import java.util.*--%>

<%--@args (String title, List<String> header, List<List<Object>> rows)--%>

<%--        @rocker.main.template(title) -> {--%>
<%--        <table class="table">--%>
<%--            <thead>--%>
<%--            <tr>--%>
<%--                @for (String item : header) {--%>
<%--                <th scope="col">@raw(item)</th>--%>
<%--                }--%>
<%--            </tr>--%>
<%--            </thead>--%>
<%--            <tbody>--%>
<%--            @for (List<Object> row : rows) {--%>
<%--                <tr>--%>
<%--                    @for (Object item : row) {--%>
<%--                    <th>@raw(item == null ? "null" : item.toString())</th>--%>
<%--                    }--%>
<%--                </tr>--%>
<%--                }--%>
<%--            </tbody>--%>
<%--        </table>--%>
<%--        }--%>

@import java.util.*

@param String title
@param List<String> header
@param List<List<Object>> rows

