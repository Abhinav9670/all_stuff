/* eslint-disable no-nested-ternary */
/* eslint-disable react/jsx-key */
/* eslint-disable react/no-array-index-key */
/* eslint-disable react/destructuring-assignment */
/* eslint-disable react/display-name */
import React, { useState } from 'react';
import { Button, Card, CardBody, CardTitle } from 'reactstrap';
import { useTable, usePagination, useSortBy } from 'react-table';
import classnames from 'classnames';

import IntlMessages from '../../helpers/IntlMessages';
import DatatablePagination from '../../components/DatatablePagination';
import Pagination from './Pagination';

import products from '../../data/products';

export default function Table({
  columns,
  data,
  divided = false,
  defaultPageSize = 10,
  setSelecteditem = () => {},
  hideHeader = false,
  hidepagination = false,
  pages = 0,
  currentPage = 1,
  onPageChange = () => {}
}) {
  const {
    getTableProps,
    getTableBodyProps,
    prepareRow,
    headerGroups,
    page,
    canPreviousPage,
    canNextPage,
    pageCount,
    gotoPage,
    setPageSize,
    state: { pageIndex, pageSize }
  } = useTable(
    {
      columns,
      data,
      initialState: { pageIndex: 0, pageSize: defaultPageSize }
    },
    useSortBy,
    usePagination
  );

  const [selectedRow, setSelectedRow] = useState(-1);

  return (
    <>
      <div style={{ height: '500px', overflow: 'scroll' }}>
        <table {...getTableProps()} className={`r-table table ${classnames({ 'table-divided': divided })}`}>
          {hideHeader !== true && (
            <thead style={{ backgroundColor: 'white' }}>
              {headerGroups.map(headerGroup => (
                <tr {...headerGroup.getHeaderGroupProps()}>
                  {headerGroup.headers.map((column, columnIndex) => (
                    <th
                      key={`th_${columnIndex}`}
                      {...column.getHeaderProps(column.getSortByToggleProps())}
                      className={column.isSorted ? (column.isSortedDesc ? 'sorted-desc' : 'sorted-asc') : ''}
                    >
                      {column.render('Header')}
                      <span />
                    </th>
                  ))}
                </tr>
              ))}
            </thead>
          )}

          <tbody {...getTableBodyProps()}>
            {page.map((row, index) => {
              prepareRow(row);
              return (
                <tr
                  {...row.getRowProps()}
                  role="button"
                  tabIndex={0}
                  onClick={() => {
                    setSelecteditem(JSON.parse(JSON.stringify(row.original)));
                    setSelectedRow(index);
                  }}
                  className={classnames('c-pointer', {
                    _active: selectedRow === index
                  })}
                >
                  {row.cells.map((cell, cellIndex) => (
                    <td
                      key={`td_${cellIndex}`}
                      {...cell.getCellProps({
                        className: cell.column.cellClass
                      })}
                    >
                      {cell.render('Cell')}
                    </td>
                  ))}
                </tr>
              );
            })}
          </tbody>
        </table>
        {!hidepagination && (
          <Pagination
            totalItems={pages}
            currentPage={currentPage}
            onChangePage={e => onPageChange(e)}
            pageSize={defaultPageSize}
          />
        )}
      </div>
    </>
  );
}

export const ReactTableWithPaginationCard = () => {
  const cols = React.useMemo(
    () => [
      {
        Header: 'Name',
        accessor: 'title',
        cellClass: 'list-item-heading w-40',
        Cell: props => <>{props.value}</>
      },
      {
        Header: 'Sales',
        accessor: 'sales',
        cellClass: 'text-muted w-10',
        Cell: props => <>{props.value}</>
      },
      {
        Header: 'Stock',
        accessor: 'stock',
        cellClass: 'text-muted w-10',
        Cell: props => <>{props.value}</>
      },
      {
        Header: 'Category',
        accessor: 'category',
        cellClass: 'text-muted w-40',
        Cell: props => <>{props.value}</>
      }
    ],
    []
  );

  return (
    <Card className="mb-4">
      <CardBody>
        <CardTitle>
          <IntlMessages id="table.react-pagination" />
        </CardTitle>
        <Table columns={cols} data={products} />
      </CardBody>
    </Card>
  );
};

export const ReactTableDivided = () => {
  const cols = React.useMemo(
    () => [
      {
        Header: 'Name',
        accessor: 'title',
        cellClass: 'list-item-heading w-40',
        Cell: props => <>{props.value}</>
      },
      {
        Header: 'Sales',
        accessor: 'sales',
        cellClass: 'text-muted  w-10',
        Cell: props => <>{props.value}</>
      },
      {
        Header: 'Stock',
        accessor: 'stock',
        cellClass: 'text-muted  w-10',
        Cell: props => <>{props.value}</>
      },
      {
        Header: 'Category',
        accessor: 'category',
        cellClass: 'text-muted  w-40',
        Cell: props => <>{props.value}</>
      }
    ],
    []
  );
  return (
    <div className="mb-4">
      <CardTitle>
        <IntlMessages id="table.divided" />
      </CardTitle>
      <Table columns={cols} data={products} divided />
    </div>
  );
};