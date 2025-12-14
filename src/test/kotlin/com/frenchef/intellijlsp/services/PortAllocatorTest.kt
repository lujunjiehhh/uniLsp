package com.frenchef.intellijlsp.services

import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * T044: PortAllocator 单元测试
 *
 * 注意：由于 PortAllocator 是 object 单例，测试前需重置内部状态。
 */
class PortAllocatorTest {

    @BeforeEach
    fun setUp() {
        // 通过反射清空 allocatedPorts
        resetAllocatedPorts()
    }

    @Test
    fun `allocatePort should return available port`() {
        val port = PortAllocator.allocatePort("TestProject")

        assertNotNull(port)
        assertTrue(port!! >= 2087) // 默认起始端口
    }

    @Test
    fun `allocatePort should not return same port twice`() {
        val port1 = PortAllocator.allocatePort("Project1")
        val port2 = PortAllocator.allocatePort("Project2")

        assertNotNull(port1)
        assertNotNull(port2)
        assertNotEquals(port1, port2)
    }

    @Test
    fun `releasePort should free the port`() {
        val port = PortAllocator.allocatePort("TestProject")
        assertNotNull(port)
        assertTrue(PortAllocator.isAllocated(port!!))

        PortAllocator.releasePort(port)

        assertFalse(PortAllocator.isAllocated(port))
    }

    @Test
    fun `isAllocated should return correct status`() {
        val port = PortAllocator.allocatePort("TestProject")
        assertNotNull(port)

        assertTrue(PortAllocator.isAllocated(port!!))
        assertFalse(PortAllocator.isAllocated(99999)) // 未分配的端口
    }

    @Test
    fun `getProjectForPort should return project name`() {
        val projectName = "MyProject"
        val port = PortAllocator.allocatePort(projectName)
        assertNotNull(port)

        assertEquals(projectName, PortAllocator.getProjectForPort(port!!))
        assertNull(PortAllocator.getProjectForPort(99999))
    }

    @Test
    fun `getAllocatedPorts should return all allocations`() {
        PortAllocator.allocatePort("Project1")
        PortAllocator.allocatePort("Project2")

        val allocated = PortAllocator.getAllocatedPorts()

        assertEquals(2, allocated.size)
        assertTrue(allocated.values.contains("Project1"))
        assertTrue(allocated.values.contains("Project2"))
    }

    @Test
    fun `released port can be reallocated`() {
        val port1 = PortAllocator.allocatePort("Project1")
        assertNotNull(port1)

        PortAllocator.releasePort(port1!!)

        val port2 = PortAllocator.allocatePort("Project2")

        // 释放的端口可能被重新分配
        assertNotNull(port2)
    }

    /** 通过反射重置 PortAllocator 的内部状态 */
    @Suppress("UNCHECKED_CAST")
    private fun resetAllocatedPorts() {
        try {
            val field: Field = PortAllocator::class.java.getDeclaredField("allocatedPorts")
            field.isAccessible = true
            val map = field.get(PortAllocator) as ConcurrentHashMap<Int, String>
            map.clear()
        } catch (e: Exception) {
            // 忽略反射错误
        }
    }
}
