import express from 'express';
import XLSX from "xlsx";
import multer from "multer";


const app = express();
const PORT = 8080;


const upload = multer({ storage: multer.memoryStorage() });

const chunkArray = (array, size) => {
  const result = [];
  for (let i = 0; i < array.length; i += size) {
    result.push(array.slice(i, i + size));
  }
  return result;
};

app.post('/fraud', upload.single('file'), async (req, res) => {
  try {
    if (!req.file) {
      return res.status(400).json({ message: "File is required" });
    }

    // read excel
    const workbook = XLSX.read(req.file.buffer, { type: "buffer" });
    const sheetName = workbook.SheetNames[0];
    const sheet = workbook.Sheets[sheetName];

    const data = XLSX.utils.sheet_to_json(sheet);

    // extract customer IDs
const customerIds = data
  .map(row => row["Customer ID"])
  .filter(id => id !== undefined && id !== null && id !== "" && id !== 0);

    // chunk into 500
    const batches = chunkArray(customerIds, 300);

    const queries = batches.map((batch, index) => {
  return `db.fraud_customers.deleteMany({
  customer_id: {
    $in: [${batch.join(", ")}]
  }
});`;
});

res.setHeader("Content-Type", "text/plain");
res.send(queries.join("\n\n"));
// res.status(200).json({
//   len : data.length
// });

  } catch (error) {
    console.error(error);
    return res.status(500).json({
      message: "Something went wrong"
    });
  }
});

// Start the server
app.listen(PORT, () => {
    console.log(`Server is running on http://localhost:${PORT}`);
});
