/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react'
import { Link } from 'react-router'
import { Menu, Icon, Layout } from 'antd'

const SubMenu = Menu.SubMenu;
const { Sider } = Layout;

interface StateStandard {
    current: string;
    collapsed: boolean;
    mode: string;
}

export default class Sidebar extends React.Component<any, StateStandard> {
    state = {
        current: 'offline-management',
        collapsed: false,
        mode: 'inline'
    }

    componentDidMount () {
        this.updateSelected()
    }
    /* eslint-disable-next-line */
    UNSAFE_componentWillReceiveProps() {
        this.updateSelected()
    }

    updateSelected = () => {
        const routes = this.props.router.routes
        console.log(routes)
        if (routes.length > 3) {
            let current = routes[3].path || 'offline-management'
            console.log(current)
            if (current.indexOf('offline-management') > -1) {
                current = 'offline-management'
            }
            this.setState({ current })
        } else {
            if (routes.length == 3) {
                let current = routes[2].path || 'offline-management'
                console.log(current)
                if (current.indexOf('task-patch-data') > -1) {
                    current = 'task-patch-data'
                }
                this.setState({ current })
            }
        }
    }

    handleClick = (e: any) => {
        this.setState({
            current: e.key
        });
    }

    onCollapse = () => {
        const { collapsed } = this.state
        // console.log(this.props)
        this.props.changeCollapsed(!collapsed)
        this.setState({ collapsed: !collapsed, mode: !collapsed ? 'vertical' : 'inline' });
    };

    render () {
        const base = '/operation-ui'
        const { collapsed, mode } = this.state
        return (
            <Layout className="sidebar m-ant-menu">
                <Sider className="dt-layout-sider" collapsed={collapsed}>
                    {/* 指标不需要头部 */}
                    <div className="dt-slider-top-icon" onClick={this.onCollapse}>
                        <Icon type={collapsed ? 'menu-unfold' : 'menu-fold'} />
                    </div>
                    <Menu
                        onClick={this.handleClick}
                        selectedKeys={[this.state.current]}
                        defaultOpenKeys={['offline']}
                        defaultSelectedKeys={[this.state.current]}
                        style={{ height: '100%', width: '100%' }}
                        mode={mode as any}
                    >
                        <SubMenu key="offline" title={
                            <span>
                                <span className="anticon">
                                    <i className="iconfont icon-menu_task" />
                                </span>
                                <span>任务与实例</span>
                            </span>
                        }>
                            <Menu.Item key="offline-management">
                                <Link to={`${base}/offline-management`}>
                                    任务管理
                                </Link>
                            </Menu.Item>
                            <Menu.Item key="offline-operation">
                                <Link to={`${base}/offline-operation`}>
                                    周期实例
                                </Link>
                            </Menu.Item>
                            <Menu.Item key="task-patch-data">
                                <Link to={`${base}/task-patch-data`}>
                                    补数据实例
                                </Link>
                            </Menu.Item>
                        </SubMenu>
                    </Menu>
                </Sider>
            </Layout>
        )
    }
}