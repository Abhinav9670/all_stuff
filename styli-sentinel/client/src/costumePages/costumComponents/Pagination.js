/* eslint-disable react/jsx-no-undef */
import React, { useEffect, useState } from 'react';
import { DropdownItem, DropdownMenu, DropdownToggle, Nav, NavItem, NavLink, UncontrolledDropdown } from 'reactstrap';
import { Colxx } from '../../components/common/CustomBootstrap';

const Pagination = ({
  totalItems = 0,
  currentPage = 1,
  numberLimit = 1000,
  lastIsActive = true,
  firstIsActive = true,
  onChangePage,
  defaultPageSize,
  showSizeSelector = false,
  pageSize: iPageSize = 10,
  className = ""
}) => {
  const pageSizePre = [20, 30, 50, 70, 100];
  const [pageSize, setPageSize] = useState(iPageSize, defaultPageSize || pageSizePre[0]);
  useEffect(() => {
    setPageSize(iPageSize);
  }, [iPageSize]);
  const [totalPage, setTotalPage] = useState(Math.floor(totalItems / pageSize));
  useEffect(() => {
    setTotalPage(Math.ceil(totalItems / pageSize));
  }, [totalItems, pageSize]);

  const onChangePageSize = val => {
    setPageSize(val);
    onChangePage(currentPage, val);
  };
  let startPoint = 1;
  let endPoint = numberLimit;

  if (numberLimit > totalPage) {
    startPoint = 1;
    endPoint = totalPage;
  } else if (currentPage <= parseInt(numberLimit / 2, 10)) {
    startPoint = 1;
    endPoint = numberLimit;
  } else if (currentPage + parseInt(numberLimit / 2, 10) <= totalPage) {
    startPoint = currentPage - parseInt(numberLimit / 2, 10);
    endPoint = currentPage + parseInt(numberLimit / 2, 10);
  } else {
    startPoint = totalPage - (numberLimit - 1);
    endPoint = totalPage;
  }
  startPoint = startPoint === 0 ? 1 : startPoint;
  const points = [];
  for (let i = startPoint; i <= endPoint; i += 1) {
    points.push(i);
  }

  const firstPageButtonClassName = currentPage <= 1 ? 'disabled' : '';
  const lastPageButtonClassName = currentPage >= totalPage ? 'disabled' : '';
  const prevPageButtonClassName = currentPage <= 1 ? 'disabled' : '';
  const nextPageButtonClassName = currentPage >= totalPage ? 'disabled' : '';
  return totalPage >= 1 ? (
    <Colxx xxs="12" className="mt-3 mb-3">
      <Nav className="justify-content-center">
        {showSizeSelector && (
          <NavItem>
            <div className="d-flex" style={{ marginTop: '14px' }}>
              <div style={{ marginTop: '7px' }}>
                Page: {pageSize * (currentPage - 1) + 1}
                &nbsp;-&nbsp;
                {totalItems < pageSize * currentPage ? totalItems : pageSize * currentPage} of {totalItems}
              </div>
              <UncontrolledDropdown className="btn-group ml-2">
                <DropdownToggle caret className="form-control text-left">
                  {pageSize}
                </DropdownToggle>
                <DropdownMenu>
                  {pageSizePre.map(pSize => {
                    return (
                      <DropdownItem key="" onClick={() => onChangePageSize(pSize)} value={pSize}>
                        {pSize}
                      </DropdownItem>
                    );
                  })}
                </DropdownMenu>
              </UncontrolledDropdown>
            </div>
          </NavItem>
        )}
        <NavItem className={`${className ? className : "ml-auto mr-0"}`}>
          <Nav className="pagination">
            {firstIsActive && (
              <NavItem className={`page-item ${firstPageButtonClassName}`}>
                <NavLink className="page-link first c-pointer" onClick={() => onChangePage(1)}>
                  <i className="simple-icon-control-start" />
                </NavLink>
              </NavItem>
            )}

            <NavItem className={`page-item ${prevPageButtonClassName}`}>
              <NavLink className="page-link prev c-pointer" onClick={() => onChangePage(currentPage - 1)}>
                <i className="simple-icon-arrow-left" />
              </NavLink>
            </NavItem>
            {points.map(i => {
              return (
                <NavItem key={i} className={`page-item ${currentPage === i && 'active'}`}>
                  <NavLink className="page-link c-pointer" onClick={() => onChangePage(i)}>
                    {i}
                  </NavLink>
                </NavItem>
              );
            })}
            <NavItem className={`page-item ${nextPageButtonClassName}`}>
              <NavLink className="page-link next c-pointer" onClick={() => onChangePage(currentPage + 1)}>
                <i className="simple-icon-arrow-right" />
              </NavLink>
            </NavItem>
            {lastIsActive && (
              <NavItem className={`page-item ${lastPageButtonClassName}`}>
                <NavLink className="page-link last c-pointer" onClick={() => onChangePage(totalPage)}>
                  <i className="simple-icon-control-end" />
                </NavLink>
              </NavItem>
            )}
          </Nav>
        </NavItem>
      </Nav>
    </Colxx>
  ) : (
    <Colxx xxs="12" className="mt-2" />
  );
};

export default Pagination;
