import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput

def Message processData(Message message) {

    // --------------------------------------------------
    // 1. Read CSV content
    // --------------------------------------------------

    def body = message.getBody(String)

    if (!body || body.trim().isEmpty()) {
        throw new Exception("Input CSV is empty")
    }

    // Remove UTF-8 BOM if present
    body = body.replaceFirst('^\uFEFF', '')

    // Split CSV into lines
    def lines = body.readLines()

    if (lines.size() < 2) {
        throw new Exception(
            "CSV must contain a header and at least one data row"
        )
    }


    // --------------------------------------------------
    // 2. Read header
    // --------------------------------------------------

    def rawHeaders = parseCSVLine(lines[0])

    // Store only headers that actually have a name
    def headers = []

    rawHeaders.eachWithIndex { header, index ->

        header = header?.trim()

        if (header && !header.isEmpty()) {

            headers.add([
                name : header,
                index: index
            ])
        }
    }


    // Validate that at least one valid header exists
    if (headers.isEmpty()) {
        throw new Exception("No valid column headers found")
    }


    // --------------------------------------------------
    // 3. Process data rows
    // --------------------------------------------------

    def data = []

    lines.drop(1).each { line ->

        if (line != null && !line.trim().isEmpty()) {

            def columns = parseCSVLine(line)

            def record = [:]

            // Only process columns that have valid headers
            headers.each { headerInfo ->

                def columnIndex = headerInfo.index
                def headerName = headerInfo.name

                def value = ""

                if (columnIndex < columns.size()) {
                    value = columns[columnIndex]?.trim() ?: ""
                }

                record[headerName] = value
            }

            data.add(record)
        }
    }


    // --------------------------------------------------
    // 4. Create BPA input structure
    // --------------------------------------------------

    def payload = [
        invocationContext: message.getProperty("invocation_context") ?: "",
        input: [
            Data: data
        ]
    ]


    // --------------------------------------------------
    // 5. Convert to JSON
    // --------------------------------------------------

    def jsonPayload = JsonOutput.toJson(payload)

    message.setBody(jsonPayload)

    message.setHeader(
        "Content-Type",
        "application/json"
    )

    return message
}


// ======================================================
// CSV Parser
// ======================================================
// Handles:
// - Commas inside quoted values
// - Quoted values
// - Escaped double quotes
// - Empty columns
// ======================================================

def parseCSVLine(String line) {

    def values = []
    def current = new StringBuilder()
    def insideQuotes = false

    for (int i = 0; i < line.length(); i++) {

        char ch = line.charAt(i)

        if (ch == '"') {

            // Escaped double quote: ""
            if (
                insideQuotes &&
                i + 1 < line.length() &&
                line.charAt(i + 1) == '"'
            ) {

                current.append('"')
                i++

            } else {

                insideQuotes = !insideQuotes
            }

        }
        else if (ch == ',' && !insideQuotes) {

            values.add(current.toString())

            current.setLength(0)

        }
        else {

            current.append(ch)
        }
    }

    // Add final column
    values.add(current.toString())

    return values
}