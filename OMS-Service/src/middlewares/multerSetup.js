const fs = require('fs');
const multer = require('multer');

function setupMulter() {
  const storage = multer.diskStorage({
    destination: function (req, file, cb) {
      // Check if the directory exists, if not create it
       const uploadDirectory = './tmp/';
      if (!fs.existsSync(uploadDirectory)) {
        fs.mkdirSync(uploadDirectory, { recursive: true });
      }
      cb(null, uploadDirectory); // Define your upload directory
    },
    filename: function (req, file, cb) {
      // Mimetype stores the file type, set extensions according to filetype
      let ext;
      switch (file.mimetype) {
        case 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet':
          ext = '.xlsx';
          break;
        case 'application/vnd.ms-excel':
          ext = '.xls';
          break;
        case 'image/jpeg':
          ext = '.jpeg';
          break;
        case 'image/png':
          ext = '.png';
          break;
        case 'image/gif':
          ext = '.gif';
          break;
        case 'text/csv':
          ext = '.csv';
          break;
        default:
          ext = ''; // Default to no extension if unknown mimetype
      }
      cb(null, file.originalname.slice(0, 4) + Date.now() + ext);
    }
  });

  return multer({ storage: storage,
    limits: {
      fileSize: 100000000
    }
   });
}

module.exports = setupMulter;
