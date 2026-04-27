import React, { useEffect, useState } from 'react';
import { Separator, Colxx } from '../components/common/CustomBootstrap';
import { Row } from 'reactstrap';
import axiosInstance from '../uitils/axios';
import { RESPONSE_STATUS } from '../uitils/responseStatus';
import "./custom.css"
import HistorySearch from './costumComponents/HistorySearch';
import { useTable, usePagination, useSortBy, useRowSelect } from 'react-table';
import classnames from 'classnames';
import { Checkbox } from './costumComponents/Checkbox';
import { createNotification } from '../costumePages/costumComponents/Notifications';
import {  getUsers, tableHeaderData } from './TableHistory';

export default function LoginHistory() {

    const [selectedUser, setSelectedUser] = useState({});
    const [selectedRow, setSelectedRow] = useState(-1);
    const [users, setUsers] = useState([]);
    const [reloadResp, setReloadResp] = useState(false);
    const [allUsers, setAllUsers] = useState([]);
    const [selectedRows, setSelected] = useState([]);
    const [loading, setLoading] = useState(false);
    const [isUser, setIsUser] = useState(true);

    let hideHeader = false;

    const {
        getTableProps,
        getTableBodyProps,
        headerGroups,
        page,
        canPreviousPage,
        canNextPage,
        pageCount,
        previousPage,
        nextPage,
        gotoPage,
        setPageSize,
        selectedFlatRows,
        pageOptions,
        prepareRow,
        state: { pageIndex, pageSize }
    } = useTable(
        {
            columns: tableHeaderData,
            data: users,
            initialState: { pageIndex: 0, pageSize: 10 }
        },
        useSortBy,
        usePagination,
        useRowSelect,
        hooks => {
            hooks.visibleColumns.push(columns => [
                {
                    id: 'selection',
                    Header: ({ getToggleAllRowsSelectedProps }) => (
                        <Checkbox {...getToggleAllRowsSelectedProps()} />
                    ),
                    Cell: ({ row }) => <Checkbox {...row.getToggleRowSelectedProps()} />
                },
                ...columns,
            ])
        }
    );

    useEffect(() => {
        if (isUser) {
          const getUsersData = async () => {
            const allData = await getUsers();
            setUsers(allData);
          };
          getUsersData();
          setIsUser(false); // Update state after fetching data to prevent infinite loop
        }
      }, [isUser]);

    const handleUserSearch = value => {
        if (!allUsers.length > 0) {
            setAllUsers([...users]);
        }
        if (value.length >= 3) {
            const regex = new RegExp(value, 'i');
            const result = users.filter(item => regex.test(item.name));
            setUsers([...result]);
        } else {
            setUsers([...allUsers]);
            setAllUsers([]);
        }
    };

    const bulkLogout = async () => {
        setLoading(true);
        let usersArray = [];
        selectedFlatRows.map((e, i) => {
          usersArray.push(e?.original);
        });
        try {
          const payload = { usersArray };
          const response = await axiosInstance.post('api/v1/auth/force-logout/select/bulk', payload);
          if (response && response.data && response.status === RESPONSE_STATUS.success) {
            createNotification({
              type: 'success',
              title: 'Bulk Users Logout',
              subtitle: 'Bulk Users Forced Logout'
            });
            setLoading(false);
            // Trigger data refresh using useEffect
            setIsUser(!isUser);
          }
          else if(response.status === 201){
            createNotification({
                type: 'error',
                title: 'Force Logout Failed',
                subtitle: "Force Logout not enabled"
              });
              setLoading(false);
          }
        } catch (error) {
          console.error('Error in Bulk Force Logout:', error);
        }
      }

    return (
        <div>
            <Row>
                <Colxx xxs="12">
                    <h1>Users Login History</h1>
                    <Separator />
                </Colxx>
            </Row>
            <Row className="mt-4">
                <Colxx xxs="12">
                    <Row>
                        <Colxx>
                            <div className="alignItm-cen mb-2 d-flex justify-content-between">
                                <HistorySearch handleUserSearch={handleUserSearch} />
                                <button type="button" disabled={selectedFlatRows.length !== 0 ? false : true} class="mr-1 btn btn-danger" onClick={() => bulkLogout()}> {loading ? "...Processing" : "Bulk Force Logout"}</button>
                            </div>
                        </Colxx>
                    </Row>
                    <div style={{ height: '500px', overflow: 'scroll' }}>
                        <table {...getTableProps()} className={`r-table table ${classnames({ 'table-divided': true })}`}>
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

                            <tbody {...getTableBodyProps()} style={{ backgroundColor: 'white' }}>
                                {page.map((row, index) => {
                                    prepareRow(row);
                                    return (
                                        <tr
                                            {...row.getRowProps()}
                                            role="button"
                                            tabIndex={0}
                                            onClick={() => {
                                                setSelectedUser(JSON.parse(JSON.stringify(row.original)));
                                                setSelectedRow(index);
                                            }}
                                            className={classnames('c-pointer', {
                                                _active: selectedRow === index,
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
                    </div>
                    <div style={{ width: '100%', display: 'flex' }}>
                        <button onClick={() => gotoPage(0)} disabled={!canPreviousPage} className='buttonClass'>
                            {'<<'}
                        </button>{' '}
                        <button className='buttonClass' onClick={() => previousPage()} disabled={!canPreviousPage}>
                            Previous
                        </button>{' '}
                        <button className='buttonClass' onClick={() => nextPage()} disabled={!canNextPage}>
                            Next
                        </button>{' '}
                        <button className='buttonClass' onClick={() => gotoPage(pageCount - 1)} disabled={!canNextPage}>
                            {'>>'}
                        </button>{'  '}
                        <div className="pagination-info">
                            <div className="page-info">
                                Page <strong>{pageIndex + 1}</strong> of <strong>{pageOptions.length}</strong>
                            </div>
                            <div className="goto-page">
                                <label for="page-input">Go to page:</label>
                                <input
                                    id="page-input"
                                    type="number"
                                    defaultValue={pageIndex + 1}
                                    onChange={e => {
                                        const pageNumber = e.target.value ? Number(e.target.value) - 1 : 0
                                        gotoPage(pageNumber)
                                    }}
                                />
                            </div>
                        </div>
                        <select className='selectClass'
                            value={pageSize}
                            onChange={e => setPageSize(Number(e.target.value))}>
                            {[10, 25, 50].map(pageSize => (
                                <option key={pageSize} value={pageSize}>
                                    {pageSize}
                                </option>
                            ))}
                        </select>
                    </div>
                </Colxx>
            </Row>
        </div>
    );
}
