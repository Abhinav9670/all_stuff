import React, { useEffect, useState } from 'react';
import { Button, Spinner } from 'reactstrap';
import { useDropzone } from 'react-dropzone';
import Papa from 'papaparse';
import Table from './costumReactTableCards';

export default function CSVdragNdrop({
  setSelectedupoload = () => {},
  showNameBadge = false,
  isLoading = false,
  getCsvData = () => {}
}) {
  const [files, setFiles] = useState([]);
  const [jsonData, setJsonData] = useState({});
  const [tableData, setTableData] = useState({});

  const { getRootProps, getInputProps, open } = useDropzone({
    accept: {
      'text/csv': []
    },
    onDrop: acceptedFiles => {
      setFiles(
        acceptedFiles.map(file =>
          Object.assign(file, {
            preview: URL.createObjectURL(file)
          })
        )
      );
    }
  });

  const fetchCsvData = async blobUrl => {
    try {
      const response = await fetch(blobUrl);
      const blob = await response.blob();

      const file = new File([blob], 'data.csv');

      Papa.parse(file, {
        complete: result => {
          setJsonData(result);
        },
        header: true,
        skipEmptyLines: true
      });
    } catch (error) {
      console.error('Error fetching and parsing CSV:', error);
    }
  };

  useEffect(async () => {
    if (files.length > 0) {
      fetchCsvData(files[0].preview);
    }
  }, [files]);

  useEffect(() => {
    getCsvData(jsonData);
  }, [tableData]);

  useEffect(() => {
    if (Object.keys(jsonData).length > 0) {
      const bodyData = jsonData.data;

      const tableHeaders = Object.keys(jsonData.data[0]);
      const tableHeaderData = [];
      const tableBodyData = jsonData.data;

      tableHeaders.map((item, index) => {
        const sample = {
          Header: item,
          accessor: item,
          cellClass: 'text-muted w-10',
          Cell: props => <>{props.value}</>
        };
        tableHeaderData.push(sample);
      });

      setTableData({
        ...tableData,
        tableHeaderData: [...tableHeaderData]
      });
    }
  }, [jsonData]);

  useEffect(() => {
    return () => files.forEach(file => URL.revokeObjectURL(file.preview));
  }, []);

  return (
    <section className="container position-relative">
      <div>
        {files.length === 0 ? (
          <div {...getRootProps({ className: 'dropzone' })}>
            <input {...getInputProps()} />
            <div className="drag-drop-field">
              <p>Drag and drop some files here or click below to open browser</p>
              <Button color="normal" className="btn btn-outline-dark" type="secondary" onClick={open}>
                Open File Dialog
              </Button>
            </div>
          </div>
        ) : (
          <div>
            {isLoading ? (
              <div
                className="position-absolute d-flex h-100 w-100 justify-content-center align-items-center"
                style={{ zIndex: '1', background: 'rgba(255,255,255,0.75)' }}
              >
                <Spinner size="lg" color="primary" />
              </div>
            ) : (
              <>
                <div
                  style={{
                    height: '100%',
                    width: '100%',
                    overflowY: 'scroll',
                    overflowX: 'scroll'
                  }}
                >
                  <p>{files[0].name}</p>
                  {tableData && tableData.tableHeaderData && (
                    <Table columns={tableData.tableHeaderData} data={jsonData.data} divided hidepagination={true} />
                  )}
                </div>
              </>
            )}
          </div>
        )}
      </div>
    </section>
  );
}
